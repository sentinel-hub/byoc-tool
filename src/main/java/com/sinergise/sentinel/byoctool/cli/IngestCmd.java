package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.TileIngestionException;
import com.sinergise.sentinel.byoctool.ingestion.CogFactory;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch.FileMap;
import com.sinergise.sentinel.byoctool.sentinelhub.AuthClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocInfoClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import software.amazon.awssdk.services.s3.S3Client;

@Command(
    name = "ingest",
    description =
        "Ingest files. It prepares Cloud Optimized GeoTIFFs, uploads them to S3 and creates tiles in the BYOC service.",
    sortOptions = false)
@Log4j2
public class IngestCmd implements Runnable {

  static final String DEFAULT_FILE_PATTERN =
      "(.*[\\/|\\\\])*(?<tile>.*)[\\/|\\\\].*\\.(?i)(tif|tiff|jp2)";

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @Parameters(index = "1", description = "Folder with tiles")
  private Path folder;

  @Option(
      names = {"-f", "--file-pattern"},
      description =
          "Regular expression to select files. Include the obligatory capture group <tile> to specify the common path of files that belong to the same tile. This also sets the tile name to the value of this capture group. The default is \"${DEFAULT-VALUE})\", which treats all tiff/jp2 files in a folder as a tile, with the folder name as the tile name. For sensing time, capture groups <year>, <month>, <day>, <hour>, <minute>, <second> and <subsecond> can be used. If not specified, no sensing time will be set. If specified, <year> is obligatory and any missing group will set that value to zero. e.g. If given only <year>, the sensing time is set to 00:00:00.0000000 UTC, January 1, <year>. Example pattern: \"(?<tile>.*)\\/.*(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})T(?<hour>[0-9]{2})(?<minute>[0-9]{2})(?<second>[0-9]{02}).*.tif\".",
      defaultValue = DEFAULT_FILE_PATTERN)
  private String filePattern;

  @Option(
      names = {"-fm", "--file-map"},
      description =
          "Provides control over which components are used from which files. Files are matched using a pattern and components can then be named. Provide a regular expression for files captured by \"--file-pattern\", one or several component indices and target band names like this: \"<Pattern>;<ComponentIndex1>:<BandName1>;<ComponentIndex2>:<BandName2>\". For example, for an RGB image where you want the band names as R,G,B: <Pattern>;1:R;2:G;3:B\". If omitted, the first component of each file will be used and the band name will equal the file name without extension.",
      paramLabel = "<fileMap>")
  private Collection<String> serializedFileMaps;

  @Option(
      names = {"--processing-folder"},
      description =
          "Path to a folder which will be used for processing COGs. By default, files are saved next to input files.")
  private Path processingFolder;

  @Option(
      names = {"--no-data"},
      description =
          "No data value. For TIFF files, the default is no data value from TIFF tag."
              + TiffDirectory.TAG_GDAL_NO_DATA_VALUE)
  private Integer noDataValue;

  @Option(
      names = {"--no-compression-predictor"},
      description =
          "Toggle to disable predictor for compression (more here https://gdal.org/drivers/raster/gtiff.html). When enabled it uses PREDICTOR=2 for integers and PREDICTOR=3 for floating points. By default, the toggle is enabled.")
  private boolean noCompressionPredictor;

  @Option(
      names = {"--num-threads"},
      description = "Number of threads to use. The default is ${DEFAULT-VALUE}.",
      defaultValue = "2")
  private int nThreads;

  @ArgGroup(exclusive = false)
  private CoverageParams coverageParams;

  private static class CoverageParams extends com.sinergise.sentinel.byoctool.cli.CoverageParams {

    @Option(
        names = {"--trace-coverage"},
        description =
            "Enables coverage tracing. See --distance-tolerance and --negative-buffer for tuning parameters. If not set the cover geometry will equal the image bounding box.",
        required = true)
    private boolean traceCoverage;
  }

  @Option(
      names = {"--dry-run"},
      description = "Toggle to skip the ingestion and just print tiles.")
  private boolean dryRun;

  @ParentCommand private ByocTool parent;

  public void run() {
    if (!Files.exists(folder)) {
      System.err.println(String.format("Folder %s does not exist!", folder));
      return;
    }

    if (serializedFileMaps == null) {
      serializedFileMaps = Collections.singleton("\\.(?i)(tif|tiff|jp2)$");
    }
    Collection<FileMap> fileMaps = FileMapsDeserialization.deserialize(serializedFileMaps);

    Collection<Tile> tiles;
    try {
      tiles = TileSearch.search(folder, Pattern.compile(filePattern), fileMaps);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    if (tiles.isEmpty()) {
      System.err.println("No tiles found!");
      return;
    }

    if (dryRun) {
      printTiles(tiles);
      return;
    }

    if (processingFolder != null) {
      if (!Files.exists(processingFolder)) {
        System.err.println(String.format("Processing folder %s does not exist!", processingFolder));
        return;
      }

      if (!Files.isDirectory(processingFolder)) {
        System.err.println(
            String.format("Processing folder %s is not a folder!", processingFolder));
        return;
      }
    }

    AuthClient authClient = parent.newAuthClient();

    ByocCollectionInfo collectionInfo =
        new ByocInfoClient(authClient)
            .getCollectionInfo(collectionId)
            .orElseThrow(() -> new RuntimeException("Collection doesn't exist."));

    ByocClient byocClient = new ByocClient(authClient, collectionInfo.getDeployment());

    S3Client s3Client = parent.newS3Client(collectionInfo.getS3Region());

    ByocIngestor ingestor =
        new ByocIngestor(byocClient, s3Client)
            .setCogFactory(
                new CogFactory()
                    .setNoDataValue(noDataValue)
                    .setUseCompressionPredictor(!noCompressionPredictor)
                    .setProcessingFolder(processingFolder))
            .setExecutorService(Executors.newFixedThreadPool(nThreads));

    if (coverageParams != null) {
      ingestor.setCoverageParams(coverageParams);
    }

    try {
      ingestor.ingest(collectionId, tiles);
    } catch (TileIngestionException e) {
      log.error(e.getMessage() + " " + String.join(" ", e.getErrors()));
    } catch (RuntimeException e) {
      log.error("Failed to ingest tiles.", e);
    }

    ingestor.getExecutorService().shutdown();
  }

  private void printTiles(Collection<Tile> tiles) {
    System.out.println("Tiles:");

    for (Tile tile : tiles) {
      System.out.print(tile.path());
      System.out.print('\t');

      if (tile.sensingTime() == null) {
        System.out.println("No sensing time");
      } else {
        System.out.println(tile.sensingTime());
      }
    }
  }
}
