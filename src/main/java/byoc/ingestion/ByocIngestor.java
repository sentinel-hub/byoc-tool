package byoc.ingestion;

import static byoc.sentinelhub.Constants.BAND_PLACEHOLDER;

import byoc.cli.CoverageParams;
import byoc.coverage.CoverageCalculator;
import byoc.sentinelhub.ByocClient;
import byoc.sentinelhub.models.ByocCollection;
import byoc.sentinelhub.models.ByocTile;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Log4j2
@Accessors(chain = true)
public class ByocIngestor {

  private static final Pattern TIFF_FILE_PATTERN = Pattern.compile("\\.(?i)tiff?$");

  private final ByocClient byocClient;

  @Setter
  private CogFactory cogFactory;

  @Setter
  private ExecutorService executorService;

  @Setter
  private S3ClientBuilder s3ClientBuilder;

  @Setter
  private CoverageParams coverageParams;

  @Setter
  private Consumer<Tile> tileIngestedCallback;

  public ByocIngestor(ByocClient byocClient) {
    this.byocClient = byocClient;
    this.cogFactory = new CogFactory();
    this.executorService = Executors.newFixedThreadPool(1);
    this.s3ClientBuilder = S3Client.builder();
  }

  public Collection<String> ingest(String collectionId, Collection<Tile> tiles) {
    List<Future<String>> futures = new ArrayList<>();

    ByocCollection collection = byocClient.getCollection(collectionId);
    if (collection == null) {
      throw new RuntimeException("Collection does not exist.");
    }

    Set<String> existingTiles = byocClient.getTilePaths(collectionId);
    Region s3Region = byocClient.getCollectionS3Region(collectionId);
    S3Client s3 = s3ClientBuilder.region(s3Region).build();

    for (Tile tile : tiles) {
      if (doesTileExist(existingTiles, tile)) {
        System.out.println(String.format("Skipping tile \"%s\" because it exists", tile.path()));
        continue;
      }

      futures.add(executorService.submit(() -> ingestTile(collection, tile, s3)));
    }

    List<String> createdTiles = new LinkedList<>();

    for (Future<String> future : futures) {
      try {
        createdTiles.add(future.get());
      } catch (ExecutionException e) {
        if (e.getCause() instanceof TileIngestionException) {
          throw (TileIngestionException) e.getCause();
        }
        throw new RuntimeException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    return createdTiles;
  }

  private boolean doesTileExist(Set<String> existingTiles, Tile tile) {
    Optional<String> optional =
        Stream.of(tile.path(), String.format("%s/%s.tiff", tile.path(), BAND_PLACEHOLDER))
            .filter(existingTiles::contains)
            .findFirst();

    return optional.isPresent();
  }

  private String ingestTile(ByocCollection collection, Tile tile, S3Client s3) throws IOException {
    Collection<String> errors = TileValidation.validate(findTiffFiles(tile));

    if (!errors.isEmpty()) {
      throw new TileIngestionException(tile, errors);
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
    errors = TileValidation.validate(cogPaths);

    if (!errors.isEmpty()) {
      throw new TileIngestionException(tile, errors);
    }

    CoverageCalculator coverageCalculator = null;
    if (coverageParams != null) {
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
      log.info(
          "Uploading image {} at index {} to s3 {}", inputFile, bandMap.index(), s3Key);
      S3Upload.uploadWithRetry(s3, collection.getS3Bucket(), s3Key, cogPath);
    }

    ByocTile byocTile = new ByocTile();
    byocTile.setPath(tile.path());
    byocTile.setSensingTime(tile.sensingTime());

    if (coverageCalculator != null) {
      byocTile.setCoverGeometry(coverageCalculator.getCoverage());
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
    private final List<InputFile> inputFiles;
  }

  @Value
  @Accessors(fluent = true)
  public static class InputFile {

    private final Path file;
    private final List<BandMap> bandMaps;
  }

  @Value
  @Accessors(fluent = true)
  public static class BandMap {

    private final int index;
    private final String name;
  }

  @Value
  @Accessors(fluent = true)
  private static class CogSource {
    private final Path inputFile;
    private final BandMap bandMap;
    private final Path cogPath;
  }

  @Getter
  public
  class TileIngestionException extends RuntimeException {

    private final Collection<String> errors;

    TileIngestionException(Tile tile, Collection<String> errors) {
      super("Failed to ingest tile with path " + tile.path());
      this.errors = errors;
    }
  }
}
