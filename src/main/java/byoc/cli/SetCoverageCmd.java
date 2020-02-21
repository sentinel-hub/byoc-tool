package byoc.cli;

import byoc.ByocTool;
import byoc.coverage.CoverageCalculator;
import byoc.sentinelhub.ByocClient;
import byoc.sentinelhub.models.ByocCollection;
import byoc.sentinelhub.models.ByocTile;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import picocli.CommandLine.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

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

  @Mixin private CoverageCalcParams coverageCalcParams;

  @Option(
      names = {"--dry-run"},
      description = "Skip update")
  private boolean dryRun;

  @ParentCommand private ByocTool parent;

  @Override
  public void run() {
    ByocClient byocClient = parent.newByocClient();
    ByocTile tile = byocClient.getTile(collectionId, tileId);
    log.info("Processing tile {}", tile.idWithPath());

    CoverageCalculator coverageCalculator = new CoverageCalculator(coverageCalcParams);

    try {
      if (file != null) {
        coverageCalculator.addImage(Paths.get(file));
      } else {
        ByocCollection collection = byocClient.getCollection(collectionId);
        S3Client s3 = parent.newS3Client(byocClient.getCollectionS3Region(collectionId));
        processTileBands(collection, tile, s3, coverageCalculator);
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

    tile.setCoverGeometry(coverage);
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