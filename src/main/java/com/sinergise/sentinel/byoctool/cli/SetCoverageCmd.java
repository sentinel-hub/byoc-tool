package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.coverage.CoverageCalculator;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import picocli.CommandLine.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

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
    S3Client s3Client = parent.newS3Client(collectionInfo.getS3Region());

    log.info("Processing tile {}", tile.idWithPath());

    try {
      if (file != null) {
        coverageCalculator.addImage(Paths.get(file));
      } else {
        processTileBands(collection, tile, s3Client, coverageCalculator);
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

    tile.setJtsCoverGeometry(coverage);
    byocClient.updateTile(collectionId, tile);
  }

  private void processTileBands(
      ByocCollection collection, ByocTile tile, S3Client s3, CoverageCalculator coverageCalculator)
      throws IOException {
    for (String band : collection.getBands()) {
      String bandPath = tile.bandPath(band);
      GetObjectRequest request =
          GetObjectRequest.builder().bucket(collection.getS3Bucket()).key(bandPath).build();

      try (InputStream is = s3.getObject(request)) {
        coverageCalculator.addImage(is);
      }
    }
  }
}
