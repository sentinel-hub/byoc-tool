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

  public List<IngestionResult> ingest(String collectionId, Collection<Tile> tiles) {
    ByocCollection collection = byocClient.getCollection(collectionId)
        .orElseThrow(() -> new CollectionNotFound(collectionId));

    CompletionService<IngestionResult> completionService =
        new ExecutorCompletionService<>(executor);

    List<Future<?>> futures = new LinkedList<>();
    for (Tile tile : tiles) {
      futures.add(completionService.submit(() -> {
        IngestionResult result;
        try {
          result = new IngestTileTask(collection, tile).ingest();
        } catch (Exception e) {
          String errors;
          if (e instanceof IngestionException) {
            log.error(e.getMessage());
            errors = e.getMessage();
          } else {
            log.error("Unexpected error occurred.", e);
            errors = "Unexpected error occurred.";
          }

          result = IngestionResult.builder()
              .tile(tile)
              .errors(errors)
              .build();
        }
        return result;
      }));
    }

    List<IngestionResult> results = new LinkedList<>();

    for (int i = 0; i < futures.size(); i++) {
      try {
        results.add(completionService.take().get());
      } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return results;
  }

  @RequiredArgsConstructor
  class IngestTileTask {

    private final ByocCollection collection;
    private final Tile tile;

    private IngestionResult ingest() throws IOException {
      Optional<ByocTile> existingTile = lookForTile();
      if (existingTile.isPresent()) {
        log.info("Skipping tile with path {} because it exists", tile.path());

        return IngestionResult.builder()
            .tile(tile)
            .tileId(existingTile.get().getId())
            .warnings(String.format("Tile with path %s already exists.", tile.path()))
            .build();
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

        return IngestionResult.builder()
            .tile(tile)
            .tileId(tileId)
            .tileCreated(true)
            .build();
      } finally {
        if (onTileIngestionEnded != null) {
          onTileIngestionEnded.accept(tile);
        }
      }
    }

    private Optional<ByocTile> lookForTile() {
      String finalPath = tile.path().contains(BAND_PLACEHOLDER) ? tile.path()
          : String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER);

      return byocClient.searchTile(collection.getId(), finalPath);
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

  @Builder
  @Getter
  public static class IngestionResult {
    Tile tile;
    String tileId;
    boolean tileCreated;
    String errors;
    String warnings;
  }
}
