package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.cli.CoverageParams;
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
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.sinergise.sentinel.byoctool.sentinelhub.Constants.BAND_PLACEHOLDER;

@Log4j2
@Accessors(chain = true)
public class ByocIngestor {

  private static final Pattern TIFF_FILE_PATTERN = Pattern.compile("\\.(?i)tiff?$");

  private final ByocClient byocClient;

  private final S3Client s3Client;

  @Setter private CogFactory cogFactory;

  @Setter @Getter private ExecutorService executorService;

  @Setter private CoverageParams coverageParams;

  @Setter private Consumer<Tile> tileStartCallback;

  @Setter private Consumer<Tile> tileIngestedCallback;

  public ByocIngestor(ByocClient byocClient, S3Client s3Client) {
    this.byocClient = byocClient;
    this.s3Client = s3Client;
    this.cogFactory = new CogFactory();
    this.executorService = Executors.newSingleThreadExecutor();
  }

  public Collection<String> ingest(String collectionId, Collection<Tile> tiles) {
    ByocCollection collection = byocClient.getCollection(collectionId);
    if (collection == null) {
      throw new RuntimeException("Collection does not exist.");
    }

    Collection<Tile> uningestedTiles = getUningestedTiles(collection, tiles);

    CompletionService<String> completionService = new ExecutorCompletionService<>(executorService);

    List<Future<?>> futures = new LinkedList<>();
    for (Tile tile : uningestedTiles) {
      futures.add(completionService.submit(() -> ingestTile(collection, tile, s3Client)));
    }

    List<String> createdTiles = new LinkedList<>();

    try {
      for (int i = 0; i < futures.size(); i++) {
          createdTiles.add(completionService.take().get());
      }
    } catch (ExecutionException e) {
      if (e.getCause() instanceof TileIngestionException) {
        throw (TileIngestionException) e.getCause();
      } else {
        throw new RuntimeException(e);
      }
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      futures.forEach(f -> f.cancel(true));
    }

    s3Client.close();

    return createdTiles;
  }

  private Collection<Tile> getUningestedTiles(ByocCollection collection, Collection<Tile> tiles) {
    Collection<Tile> uningestedTiles = new LinkedList<>();
    Set<String> ingestedPaths = byocClient.getTilePaths(collection.getId());

    for (Tile tile : tiles) {
      String pathWithPlaceholder = String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER);
      String[] pathsToCheck = {tile.path(), pathWithPlaceholder};
      boolean pathExist = Arrays.stream(pathsToCheck).anyMatch(ingestedPaths::contains);

      if (pathExist) {
        System.out.println(String.format("Skipping tile \"%s\" because it exists", tile.path()));
      } else {
        uningestedTiles.add(tile);
      }
    }

    return uningestedTiles;
  }

  private String ingestTile(ByocCollection collection, Tile tile, S3Client s3Client)
      throws IOException {

    if (tileStartCallback != null) {
      tileStartCallback.accept(tile);
    }

    List<Path> tiffFiles = findTiffFiles(tile);

    if (!tiffFiles.isEmpty()) {
      Collection<String> errors = TileValidation.validate(tiffFiles);

      if (!errors.isEmpty()) {
        throw new TileIngestionException(tile, errors);
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
      throw new TileIngestionException(tile, errors);
    }

    CoverageCalculator coverageCalculator = null;
    if (coverageParams != null && tile.coverage() == null) {
      coverageCalculator = new CoverageCalculator(coverageParams);
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
      S3Upload.uploadWithRetry(s3Client, collection.getS3Bucket(), s3Key, cogPath);
    }

    ByocTile byocTile = new ByocTile();
    byocTile.setPath(tile.path());
    byocTile.setSensingTime(tile.sensingTime());

    if (coverageCalculator != null) {
      byocTile.setJtsCoverGeometry(coverageCalculator.getCoverage());
    } else if (tile.coverage() != null) {
      byocTile.setCoverGeometry(tile.coverage());
    }

    log.info("Creating tile {}", tile.path());

    String tileId = byocClient.createTile(collection.getId(), byocTile);

    if (tileIngestedCallback != null) {
      tileIngestedCallback.accept(tile);
    }

    return tileId;
  }

  private List<Path> findTiffFiles(Tile tile) {
    return tile.inputFiles().stream()
        .map(InputFile::file)
        .filter(path -> TIFF_FILE_PATTERN.matcher(path.toString()).find())
        .collect(Collectors.toList());
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

  @Getter
  public static class TileIngestionException extends RuntimeException {

    private final Collection<String> errors;

    TileIngestionException(Tile tile, Collection<String> errors) {
      super(String.format("Failed to ingest tile with path %s.", tile.path()));
      this.errors = errors;
    }
  }
}
