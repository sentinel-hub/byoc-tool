package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.ingestion.CogFactory;
import com.sinergise.sentinel.byoctool.ingestion.ProcessUtil;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch.FileMap;
import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;
import com.sinergise.sentinel.byoctool.ingestion.storage.S3StorageClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

@Command(
    name = "ingest",
    description =
        "Ingest files. It prepares Cloud Optimized GeoTIFFs, uploads them to S3 and creates tiles in the BYOC service.",
    sortOptions = false)
@Log4j2
public class IngestCmd implements Runnable {

  public static final String DEFAULT_FILE_PATTERN =
      "(.*[\\/|\\\\])*(?<tile>.*)[\\/|\\\\].*\\.(?i)(tif|tiff|jp2)";

  public static final String DEFAULT_COG_STORAGE_FOLDER = "<tile>";

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @Parameters(index = "1", description = "Folder with tiles")
  private Path folder;

  @Option(
      names = {"-f", "--file-pattern"},
      description =
          "This is a regular expression which will be used to collect and group files for processing into COGs. Can be used in one of two ways. You need to either define `<tile>` or custom named capture groups. If the capture group `<tile>` is specified it will represent the common path of files which belong to the same tile. This common path will also be the folder in the cloud where the generated COGs will be uploaded to. Alternatively, if `<tile>` is not specified, you need to set the --cog-stage-folder parameter and define custom named capture groups here. This allows for more flexibility than `<tile>` and is useful for constructing the common path(s) using your defined capture groups; useful if your files are in different folders, for example. For sensing time, capture groups <year>, <month>, <day>, <hour>, <minute>, <second> and <subsecond> can be used. If not specified, no sensing time will be set. If specified, <year> is obligatory and any missing group will set that value to zero. e.g. If given only <year>, the sensing time is set to 00:00:00.0000000 UTC, January 1, <year>. Example pattern: \"(?<tile>.*)\\/.*(?<year>[0-9]{4})(?<month>[0-9]{2})(?<day>[0-9]{2})T(?<hour>[0-9]{2})(?<minute>[0-9]{2})(?<second>[0-9]{02}).*.tif\".",
      defaultValue = DEFAULT_FILE_PATTERN)
  private String filePattern;

  @Option(
      names = {"--cog-storage-folder"},
      description =
          "Defines the folder in your cloud storage where the generated COGs will be uploaded to. Use this in combination with --file-pattern if you need more flexibility in grouping files than is provided with `<tile>`. Any named capture group defined in --file-pattern can be used here. To use a named capture group, specify it as `<myGroupName>` and the captured value will be used instead. Files with equal storage folder will be interpreted as files from the same tile. Here's an example with multiple capture groups. Let's say you have tiles that have files in different folders, for example a tile with date 2020-06-01 has files in data/SR/2020-06-01.tif and data/QA/2020-06-01.tif. You cannot define a single capture group to capture both `data` and `2020-06-01`. Thus, you need to use multiple regex groups. You could select files as --file-pattern=\".*(?<firstPart>data/)(SR|QA)/(?<secondPart>.*(?<year>[0-9]{4})-(?<month>[0-9]{2})-(?<day>[0-9]{2}))\\.tif\" and then define cloud storage folder as --cog-storage-folder=\"<firstPart><secondPart>\", which would upload the tile 2020-06-01 in cloud to folder `data/2020-06-01`.",
      defaultValue = DEFAULT_COG_STORAGE_FOLDER)
  private String cogStorageFolder;

  @Option(
      names = {"-fm", "--file-map"},
      description =
          "Provides control over which components are used from which files. Files are matched using a pattern and components can then be named. Provide a regular expression for files captured by \"--file-pattern\", one or several component indices and target band names like this: \"<Pattern>;<ComponentIndex1>:<BandName1>;<ComponentIndex2>:<BandName2>\". For example, for an RGB image where you want the band names as R,G,B: <Pattern>;1:R;2:G;3:B\". If omitted, the first component of each file will be used and the band name will equal the file name without extension.",
      paramLabel = "<fileMap>")
  private Collection<String> serializedFileMaps;

  @Option(names = "--resampling", description = "Sets resampling algorithms for COG overviews. Available algorithms are listed here https://gdal.org/programs/gdal_translate.html#cmdoption-gdal_translate-r. If specified only once, it also applies to all bands. Otherwise, you need to repeat as many times as there are bands.", defaultValue = "average")
  String[] resampling;

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
          "Disables predictor for compression (more here https://gdal.org/drivers/raster/gtiff.html). When enabled it uses PREDICTOR=2 for integers and PREDICTOR=3 for floating points. By default, the toggle is enabled.")
  private boolean noCompressionPredictor;

  @Option(
      names = {"--num-threads"},
      description = "Number of threads to use. The default is ${DEFAULT-VALUE}.",
      defaultValue = "2")
  private int nThreads;

  @ArgGroup(exclusive = false)
  private CoverageTracingConfig tracingConfig;

  private static class CoverageTracingConfig extends com.sinergise.sentinel.byoctool.cli.CoverageTracingConfig {

    @Option(
        names = {"--trace-coverage"},
        description =
            "Enables coverage tracing. See --distance-tolerance and --negative-buffer for tuning parameters. If not set the cover geometry will equal the image bounding box.",
        required = true)
    private boolean traceCoverage;
  }

  @Option(
      names = {"--multipart-upload", "--aws-multipart-upload"},
      description = "Enables multipart upload for AWS.")
  private boolean multipartUpload;

  @Option(
      names = {"--dry-run"},
      description = "Skips the ingestion and just prints found tiles.")
  private boolean dryRun;

  @Option(
      names = {"--delete-generated-cogs"},
      description = "Deletes generated COGs after they are processed and uploaded. Will leave them on disk if not set.")
  private boolean deleteGeneratedCogs;

  @ParentCommand private ByocTool parent;

  public void run() {
    if (!Files.exists(folder)) {
      System.err.println(String.format("Folder %s does not exist!", folder));
      return;
    }

    if (serializedFileMaps == null) {
      serializedFileMaps = Collections.singleton("\\.(?i)(tif|tiff|jp2)$");
    }
    List<FileMap> fileMaps = FileMapsDeserialization.deserialize(serializedFileMaps);
    setResampling(fileMaps);

    Collection<Tile> tiles;
    try {
      tiles = TileSearch.search(folder, Pattern.compile(filePattern), fileMaps, cogStorageFolder);
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
        System.err.printf("Processing folder %s does not exist!%n", processingFolder);
        return;
      }

      if (!Files.isDirectory(processingFolder)) {
        System.err.printf("Processing folder %s is not a folder!%n", processingFolder);
        return;
      }
    }

    ByocCollectionInfo collectionInfo = parent.getCollectionInfo(collectionId);
    ByocClient byocClient = parent.newByocClient(collectionInfo.getDeployment());

    ObjectStorageClient objectStorageClient = parent.newObjectStorageClient(collectionInfo);

    if (multipartUpload && objectStorageClient instanceof S3StorageClient) {
      ((S3StorageClient) objectStorageClient).setMultipartUpload(multipartUpload);
    }

    CogFactory cogFactory = new CogFactory()
        .setNoDataValue(noDataValue)
        .setUseCompressionPredictor(!noCompressionPredictor)
        .setProcessingFolder(processingFolder);

    ExecutorService executor = Executors.newFixedThreadPool(nThreads);

    ByocIngestor ingestor = new ByocIngestor(byocClient, objectStorageClient)
        .setExecutor(executor)
        .setCogFactory(cogFactory)
        .setTracingConfig(tracingConfig)
        .setDeleteGeneratedCogs(deleteGeneratedCogs);

    String gdalVersion = ProcessUtil.runCommand("gdalinfo", "--version");
    log.debug("GDAL version: {}", gdalVersion);

    try {
      ingestor.ingest(collectionId, tiles);
    } finally {
      executor.shutdown();
      objectStorageClient.close();
    }
  }

  void setResampling(List<FileMap> fileMaps) {
    int bandCount = 0;
    boolean notEnoughValues = false;

    for (FileMap fileMap : fileMaps) {
      for (BandMap bandMap : fileMap.bands()) {
        if (resampling.length == 1) {
          bandMap.setResampling(resampling[bandCount]);
        } else if (bandCount < resampling.length) {
          bandMap.setResampling(resampling[bandCount++]);
        } else {
          notEnoughValues = true;
          break;
        }
      }
    }

    if (notEnoughValues || (resampling.length != 1 && bandCount < resampling.length)) {
      throw new IllegalArgumentException("--resampling is not configured properly!");
    }
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
