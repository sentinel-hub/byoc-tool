package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.cli.CoverageTracingConfig;
import com.sinergise.sentinel.byoctool.coverage.CoverageCalculator;
import com.sinergise.sentinel.byoctool.ingestion.IngestionException.CollectionNotFound;
import com.sinergise.sentinel.byoctool.ingestion.IngestionException.TileInvalid;
import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import com.sinergise.sentinel.byoctool.utils.JtsUtils;
import lombok.*;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.geojson.GeoJsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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
  private final ObjectStorageClient objectStorageClient;

  @Setter
  private Executor executor = ForkJoinPool.commonPool();

  @Setter
  private CogFactory cogFactory = new CogFactory();

  @Setter
  private CoverageTracingConfig tracingConfig;

  @Setter
  private boolean deleteGeneratedCogs;

  @Setter
  private Consumer<Tile> onTileIngestionStarted;

  @Setter
  private Consumer<Tile> onTileIngestionEnded;

  @Setter
  private Consumer<Tile> onTileIngested;

  public Collection<String> ingest(String collectionId, Collection<Tile> tiles) {
    ByocCollection collection = byocClient.getCollection(collectionId)
        .orElseThrow(() -> new CollectionNotFound(collectionId));

    CompletionService<Optional<String>> completionService =
        new ExecutorCompletionService<>(executor);

    List<Future<?>> futures = new LinkedList<>();
    for (Tile tile : tiles) {
      futures.add(completionService.submit(() ->
          new IngestTask(collection, tile).ingest()));
    }

    List<String> tileIds = new LinkedList<>();

    for (int i = 0; i < futures.size(); i++) {
      try {
        completionService.take().get()
            .ifPresent(tileIds::add);
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

    return tileIds;
  }

  @RequiredArgsConstructor
  class IngestTask {

    private final ByocCollection collection;
    private final Tile tile;

    private Optional<String> ingest() throws IOException {
      if (doesTileExist()) {
        log.info("Skipping tile {} because it exists", tile.path());
        return Optional.empty();
      }

      if (onTileIngestionStarted != null) {
        onTileIngestionStarted.accept(tile);
      }

      try {
        ByocTile byocTile = new ByocTile();
        byocTile.setPath(tile.path());
        byocTile.setSensingTime(tile.sensingTime());
        byocTile.setCoverGeometry(processFiles());
        String tileId = byocClient.createTile(collection.getId(), byocTile).getId();

        if (onTileIngested != null) {
          onTileIngested.accept(tile);
        }

        return Optional.of(tileId);
      } finally {
        if (onTileIngestionEnded != null) {
          onTileIngestionEnded.accept(tile);
        }
      }
    }

    private boolean doesTileExist() {
      String finalPath = tile.path().contains(BAND_PLACEHOLDER) ? tile.path()
          : String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER);

      return byocClient.searchTile(collection.getId(), finalPath).isPresent();
    }

    private GeoJsonObject processFiles() throws IOException {
      validateFiles(findTiffFiles(tile));

      Collection<CogSource> cogSources = new LinkedList<>();

      for (InputFile inputFile : tile.inputFiles()) {
        for (BandMap bandMap : inputFile.bandMaps()) {

          log.info("Creating COG out of image {} at index {}", inputFile.path(), bandMap.index());
          Path cogFile = cogFactory.createCog(tile, inputFile.path(), bandMap);

          cogSources.add(new CogSource(inputFile.path(), bandMap, cogFile));
        }
      }

      List<Path> cogPaths = cogSources.stream().map(CogSource::cogPath).collect(Collectors.toList());
      validateFiles(cogPaths);

      CoverageCalculator coverageCalculator = null;
      if (tracingConfig != null && tile.coverage() == null) {
        coverageCalculator = new CoverageCalculator(tracingConfig);
      }

      for (CogSource cogSource : cogSources) {
        Path inputFile = cogSource.inputPath();
        BandMap bandMap = cogSource.bandMap();
        Path cogPath = cogSource.cogPath();

        if (coverageCalculator != null) {
          log.info("Tracing coverage in image {} at index {}", inputFile, bandMap.index());
          coverageCalculator.addImage(cogPath);
        }

        String s3Key = String.format("%s/%s.tiff", tile.path(), bandMap.name());
        log.info("Uploading image {} at index {} to s3 {}", inputFile, bandMap.index(), s3Key);

        objectStorageClient.store(collection.getS3Bucket(), s3Key, cogPath);
        
        if (deleteGeneratedCogs) {
          Files.delete(cogPath);
        }
      }

      if (coverageCalculator != null) {
        return JtsUtils.toGeoJson(coverageCalculator.getCoverage());
      } else if (tile.coverage() != null) {
        return tile.coverage();
      }

      return null;
    }

    private List<Path> findTiffFiles(Tile tile) {
      return tile.inputFiles().stream()
          .map(InputFile::path)
          .filter(path -> TIFF_FILE_PATTERN.matcher(path.toString()).find())
          .collect(Collectors.toList());
    }

    private void validateFiles(List<Path> cogPaths) throws IOException {
      Collection<String> errors = TileValidation.validate(cogPaths);

      if (!errors.isEmpty()) {
        throw new TileInvalid(tile, errors);
      }
    }
  }

  @Value
  @Accessors(fluent = true)
  @Builder
  public static class Tile {

    String path;
    Instant sensingTime;
    GeoJsonObject coverage;
    List<InputFile> inputFiles;
  }

  @Value
  @Accessors(fluent = true)
  public static class InputFile {

    Path path;
    List<BandMap> bandMaps;
  }

  @RequiredArgsConstructor
  @Getter
  @Accessors(fluent = true)
  public static class BandMap {

    private final int index;
    private final String name;
    private int[] overviewLevels;
    private Integer minSize;
    private String resampling;

    public BandMap setOverviewLevels(int[] levels) {
      Objects.requireNonNull(levels);
      this.overviewLevels = levels;
      return this;
    }

    public BandMap setMinSize(int minSize) {
      this.minSize = minSize;
      return this;
    }

    public BandMap setResampling(String resampling) {
      this.resampling = resampling;
      return this;
    }
  }

  @Value
  @Accessors(fluent = true)
  private static class CogSource {

    Path inputPath;
    BandMap bandMap;
    Path cogPath;
  }
}
