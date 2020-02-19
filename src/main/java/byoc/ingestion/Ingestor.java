package byoc.ingestion;

import static byoc.sentinelhub.Constants.BAND_PLACEHOLDER;

import byoc.commands.CoverageCalcParams;
import byoc.coverage.CoverageCalculator;
import byoc.ingestion.TileSearch.BandSource;
import byoc.ingestion.TileSearch.FileSource;
import byoc.ingestion.TileSearch.Tile;
import byoc.sentinelhub.ByocClient;
import byoc.sentinelhub.models.ByocTile;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;

@Log4j2
public class Ingestor {

  private static final Pattern TIFF_FILE_PATTERN = Pattern.compile("\\.(?i)tiff?$");

  private static final int COVERAGE_MAXIMUM_POINTS = 100;

  private final ByocClient byocClient;
  private final ExecutorService executorService;
  private final CogFactory cogFactory;
  private final String collectionId;
  private final String s3Bucket;
  private final Set<String> existingTilePaths;
  private final S3Uploader s3Uploader;

  private CoverageCalcParams coverageCalcParams;

  public Ingestor(
      String collectionId, ByocClient byocClient, int nThreads, CogFactory cogFactory) {
    this.byocClient = byocClient;
    this.executorService = Executors.newFixedThreadPool(nThreads);
    this.cogFactory = cogFactory;
    this.collectionId = collectionId;
    this.s3Bucket = byocClient.getCollection(collectionId).getS3Bucket();
    this.existingTilePaths = byocClient.getAllTilePaths(collectionId);
    this.s3Uploader = new S3Uploader(byocClient.getS3ClientForCollection(collectionId));
  }

  public void setCoverageCalcParams(CoverageCalcParams coverageCalcParams) {
    this.coverageCalcParams = coverageCalcParams;
  }

  public void ingest(Collection<Tile> tiles) {
    List<Future<Void>> futures = new ArrayList<>();

    for (Tile tile : tiles) {
      if (doesTileExist(tile)) {
        System.out.println(String.format("Skipping tile \"%s\" because it exists", tile.path()));
        continue;
      }

      futures.add(
          executorService.submit(
              () -> {
                ingestTile(tile);

                return null;
              }));
    }

    executorService.shutdown();

    for (Future<Void> future : futures) {
      try {
        future.get();
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    try {
      executorService.awaitTermination(10, TimeUnit.DAYS);
    } catch (InterruptedException e) { // ignore
    }

    s3Uploader.close();
  }

  private boolean doesTileExist(Tile tile) {
    Optional<String> optional =
        Stream.of(tile.path(), String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER))
            .filter(existingTilePaths::contains)
            .findFirst();

    return optional.isPresent();
  }

  private void ingestTile(Tile tile) throws IOException {
    Collection<String> errors = TileValidation.validate(getTiffFiles(tile));

    if (!errors.isEmpty()) {
      printValidationErrors(tile, errors);
      return;
    }

    Collection<CogSource> cogSources = new LinkedList<>();

    for (FileSource fileSource : tile.fileSources()) {
      for (BandSource bandSource : fileSource.bandSources()) {

        log.info("Creating COG out of image {} at index {}", fileSource.path(), bandSource.index());
        Path cogPath = cogFactory.createCog(tile, fileSource.path(), bandSource);

        cogSources.add(new CogSource(fileSource, bandSource, cogPath));
      }
    }

    List<Path> cogPaths = cogSources.stream().map(CogSource::cogPath).collect(Collectors.toList());
    errors = TileValidation.validate(cogPaths);

    if (!errors.isEmpty()) {
      printValidationErrors(tile, errors);
      return;
    }

    CoverageCalculator coverageCalculator = null;
    if (coverageCalcParams != null) {
      coverageCalculator = new CoverageCalculator(coverageCalcParams);
    }

    for (CogSource cogSource : cogSources) {
      FileSource fileSource = cogSource.fileSource();
      BandSource bandSource = cogSource.bandSource();
      Path cogPath = cogSource.cogPath();

      if (coverageCalculator != null) {
        log.info("Tracing coverage in image {} at index {}", fileSource.path(), bandSource.index());
        coverageCalculator.addImage(cogPath);
      }

      String s3Key = String.format("%s/%s.tiff", tile.path(), bandSource.name());
      log.info(
          "Uploading image {} at index {} to s3 {}", fileSource.path(), bandSource.index(), s3Key);
      s3Uploader.uploadWithRetry(s3Bucket, s3Key, cogPath);
    }

    ByocTile byocTile = new ByocTile();
    byocTile.setPath(tile.path());
    byocTile.setSensingTime(tile.sensingTime());

    if (coverageCalculator != null) {
      Geometry coverage = coverageCalculator.getCoverage();
      int numPoints = coverage.getNumPoints();

      if (numPoints > COVERAGE_MAXIMUM_POINTS) {
        printTooManyPointsError(tile, numPoints);
        return;
      }

      byocTile.setCoverGeometry(coverage);
    }

    log.info("Creating tile {}", tile.path());

    try {
      byocClient.createTile(collectionId, byocTile);
    } catch (RuntimeException e) {
      System.err.println(e.getMessage());
    }
  }

  private List<Path> getTiffFiles(Tile tile) {
    return tile.fileSources().stream()
        .map(FileSource::path)
        .filter(path -> TIFF_FILE_PATTERN.matcher(path.toString()).find())
        .collect(Collectors.toList());
  }

  private void printValidationErrors(Tile tile, Collection<String> errors) {
    for (String error : errors) {
      printTileError(tile, error);
    }
  }

  private void printTooManyPointsError(Tile tile, int numPoints) {
    printTileError(
        tile,
        String.format(
            "Coverage with too many points. It has %d points, but it should have at-most %d points.",
            numPoints, COVERAGE_MAXIMUM_POINTS));
  }

  private void printTileError(Tile tile, String message) {
    System.err.println(
        String.format("Failed to ingest tile with path \"%s\". Reason: %s", tile.path(), message));
  }

  @Value
  @Accessors(fluent = true)
  private static class CogSource {
    private final FileSource fileSource;
    private final BandSource bandSource;
    private final Path cogPath;
  }
}
