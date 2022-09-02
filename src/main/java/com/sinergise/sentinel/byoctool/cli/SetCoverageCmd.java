package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.coverage.CoverageCalculator;
import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import com.sinergise.sentinel.byoctool.utils.JtsUtils;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import picocli.CommandLine.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;

@Command(name = "set-coverage", description = "Set tile coverage.")
@Log4j2
public class SetCoverageCmd implements Runnable {

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @Parameters(index = "1", description = "Tile id")
  private String tileId;

  @Option(
      names = "--file",
      description =
          "Optionally set the path to a local file corresponding to the input tile which will be used for coverage tracing. If omitted, the file will be accessed via S3.")
  private String file;

  @Mixin private CoverageTracingConfig coverageTracingConfig;

  @Option(
      names = {"--dry-run"},
      description = "Skip update")
  private boolean dryRun;

  @ParentCommand private ByocTool parent;

  @Override
  public void run() {
    ByocCollectionInfo collectionInfo = parent.getCollectionInfo(collectionId);
    ByocClient byocClient = parent.newByocClient(collectionInfo.getDeployment());

    ByocCollection collection = byocClient.getCollection(collectionId)
        .orElseThrow(() -> new RuntimeException("Collection not found."));

    ByocTile tile = byocClient.getTile(collectionId, tileId)
        .orElseThrow((() -> new RuntimeException("Tile not found.")));

    CoverageCalculator coverageCalculator = new CoverageCalculator(coverageTracingConfig);
    ObjectStorageClient objectStorageClient = parent.newObjectStorageClient(collectionInfo);
    log.debug("Processing tile {} (id = {})", tile.getPath(), tile.getId());

    try {
      if (file != null) {
        coverageCalculator.addImage(Paths.get(file));
      } else {
        processTileBands(collection, tile, objectStorageClient, coverageCalculator);
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    Geometry coverage = coverageCalculator.getCoverage();
    System.out.println(coverage);
    System.out.println("CRS: " + coverage.getSRID());

    if (dryRun) {
      return;
    }

    tile.setCoverGeometry(JtsUtils.toGeoJson(coverage));

    byocClient.updateTile(collectionId, tile);
  }

  private void processTileBands(ByocCollection collection, ByocTile tile, ObjectStorageClient objectStorageClient,
      CoverageCalculator coverageCalculator) throws IOException {
    for (String band : collection.getBands()) {
      String bandPath = tile.getPath().replace(ByocTile.BAND_PLACEHOLDER, band);

      try (InputStream is = objectStorageClient.getObjectAsStream(collection.getS3Bucket(), bandPath)) {
        coverageCalculator.addImage(is);
      }
    }
  }
}
