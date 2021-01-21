package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.cli.CoverageTracingConfig;
import com.sinergise.sentinel.byoctool.coverage.CoverageCalculator;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.geojson.GeoJsonObject;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile.BAND_PLACEHOLDER;

@Log4j2
@RequiredArgsConstructor
@Accessors(chain = true)
public class ByocIngestor {

  private static final Pattern TIFF_FILE_PATTERN = Pattern.compile("\\.(?i)tiff?$");

  private final ByocClient byocClient;

  private final S3Client s3Client;

  @Setter private Executor executor = ForkJoinPool.commonPool();

  @Setter private CogFactory cogFactory = new CogFactory();

  @Setter private CoverageTracingConfig tracingConfig;

  @Setter private boolean multipartUpload;

  @Setter private Consumer<Tile> tileStartCallback;

  @Setter private Consumer<Tile> tileIngestedCallback;

  public Collection<String> ingest(String collectionId, Collection<Tile> tiles) {
    ByocCollection collection = byocClient.getCollection(collectionId)
        .orElseThrow(() -> new RuntimeException("Collection not found."));

    CompletionService<Optional<String>> completionService =
        new ExecutorCompletionService<>(executor);

    List<Future<?>> futures = new LinkedList<>();
    for (Tile tile : tiles) {
      futures.add(completionService.submit(() -> ingestTile(collection, tile)));
    }

    List<String> createdTiles = new LinkedList<>();

    for (int i = 0; i < futures.size(); i++) {
      try {
        completionService.take().get()
            .ifPresent(createdTiles::add);
      } catch (ExecutionException e) {
        if (e.getCause() instanceof IngestionException) {
          log.error(e.getMessage());
        } else {
          log.error("Unexpected error occurred", e);
        }
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return createdTiles;
  }

  private Optional<String> ingestTile(ByocCollection collection, Tile tile) throws IOException {
    if (doesTileExist(collection, tile)) {
      System.out.println(String.format("Skipping tile \"%s\" because it exists", tile.path()));
      return Optional.empty();
    }

    if (tileStartCallback != null) {
      tileStartCallback.accept(tile);
    }

    List<Path> tiffFiles = findTiffFiles(tile);

    if (!tiffFiles.isEmpty()) {
      Collection<String> errors = TileValidation.validate(tiffFiles);

      if (!errors.isEmpty()) {
        throw new IngestionException(tile, errors);
      }
    }

    Collection<CogSource> cogSources = new LinkedList<>();

    for (InputFile inputFile : tile.inputFiles()) {
      for (BandMap bandMap : inputFile.bandMaps()) {

        log.info("Creating COG out of image {} at index {}", inputFile.file(), bandMap.index());
        Path cogFile = cogFactory.createCog(tile, inputFile.file(), bandMap);

        cogSources.add(new CogSource(inputFile.file(), bandMap, cogFile));
      }
    }

    List<Path> cogPaths = cogSources.stream().map(CogSource::cogPath).collect(Collectors.toList());
    Collection<String> errors = TileValidation.validate(cogPaths);

    if (!errors.isEmpty()) {
      throw new IngestionException(tile, errors);
    }

    CoverageCalculator coverageCalculator = null;
    if (tracingConfig != null && tile.coverage() == null) {
      coverageCalculator = new CoverageCalculator(tracingConfig);
    }

    for (CogSource cogSource : cogSources) {
      Path inputFile = cogSource.inputFile();
      BandMap bandMap = cogSource.bandMap();
      Path cogPath = cogSource.cogPath();

      if (coverageCalculator != null) {
        log.info("Tracing coverage in image {} at index {}", inputFile, bandMap.index());
        coverageCalculator.addImage(cogPath);
      }

      String s3Key = String.format("%s/%s.tiff", tile.path(), bandMap.name());
      log.info("Uploading image {} at index {} to s3 {}", inputFile, bandMap.index(), s3Key);

      uploadToS3(collection, cogPath, s3Key);
    }

    ByocTile byocTile = new ByocTile();
    byocTile.setPath(tile.path());
    byocTile.setSensingTime(tile.sensingTime());

    if (coverageCalculator != null) {
      byocTile.setJtsCoverGeometry(coverageCalculator.getCoverage());
    } else if (tile.coverage() != null) {
      byocTile.setCoverGeometry(tile.coverage());
    }

    String tileId = byocClient.createTile(collection.getId(), byocTile).getId();

    if (tileIngestedCallback != null) {
      tileIngestedCallback.accept(tile);
    }

    return Optional.of(tileId);
  }

  private boolean doesTileExist(ByocCollection collection, Tile tile) {
    String finalPath = tile.path().contains(BAND_PLACEHOLDER) ? tile.path()
        : String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER);

    return byocClient.searchTile(collection.getId(), finalPath).isPresent();
  }

  private List<Path> findTiffFiles(Tile tile) {
    return tile.inputFiles().stream()
        .map(InputFile::file)
        .filter(path -> TIFF_FILE_PATTERN.matcher(path.toString()).find())
        .collect(Collectors.toList());
  }

  private void uploadToS3(ByocCollection collection, Path cogPath, String s3Key) {
    if (multipartUpload) {
      S3MultiPartUploader.upload(s3Client, collection.getS3Bucket(), s3Key, cogPath);
    } else {
      PutObjectRequest request = PutObjectRequest.builder().bucket(collection.getS3Bucket()).key(s3Key).build();
      s3Client.putObject(request, RequestBody.fromFile(cogPath));
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class Tile {

    private final String path;
    private final LocalDateTime sensingTime;
    private final GeoJsonObject coverage;
    private final List<InputFile> inputFiles;
  }

  @Value
  @Accessors(fluent = true)
  public static class InputFile {


    private final Path file;
    private final List<BandMap> bandMaps;
  }

  @RequiredArgsConstructor
  @Getter
  @Accessors(fluent = true)
  public static class BandMap {


    private final int index;
    private final String name;
    private int[] overviewLevels;
    private Integer minSize;

    public BandMap setOverviewLevels(int[] levels) {
      Objects.requireNonNull(levels);
      this.overviewLevels = levels;
      return this;
    }

    public BandMap setMinSize(int minSize) {
      this.minSize = minSize;
      return this;
    }
  }

  @Value
  @Accessors(fluent = true)
  private static class CogSource {

    private final Path inputFile;
    private final BandMap bandMap;
    private final Path cogPath;
  }
}
