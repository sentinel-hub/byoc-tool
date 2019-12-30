package byoc.commands;

import byoc.ByocTool;
import byoc.coverage.CoverageCalculator;
import byoc.sentinelhub.ByocService;
import byoc.sentinelhub.models.ByocCollection;
import byoc.sentinelhub.models.ByocTile;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import lombok.extern.log4j.Log4j2;
import org.locationtech.jts.geom.Geometry;
import picocli.CommandLine.*;

@Command(name = "set-coverage", description = "Set tile coverage.")
@Log4j2
public class SetCoverageCmd implements Runnable {

  @Parameters(index = "0", description = "Collection id")
  private String collectionId;

  @Parameters(index = "1", description = "Tile id")
  private String tileId;

  @Option(names = "--file", description = "Local file to use instead of a file in S3")
  private String file;

  @Mixin private CoverageCalcParams coverageCalcParams;

  @Option(
      names = {"--dry-run"},
      description = "Skip update")
  private boolean dryRun;

  @ParentCommand private ByocTool parent;

  @Override
  public void run() {
    ByocService byocService = parent.getByocService();
    ByocTile tile = byocService.getTile(collectionId, tileId);
    log.info("Processing tile {}", tile.idWithPath());

    CoverageCalculator coverageCalculator = new CoverageCalculator(coverageCalcParams);

    try {
      if (file != null) {
        coverageCalculator.addImage(Paths.get(file));
      } else {
        ByocCollection collection = byocService.getCollection(collectionId);
        AmazonS3 s3Service = byocService.getS3ClientForCollection(collectionId);
        processTileBands(collection, tile, s3Service, coverageCalculator);
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
    byocService.updateTile(collectionId, tile);
  }

  private void processTileBands(
      ByocCollection collection,
      ByocTile tile,
      AmazonS3 s3Service,
      CoverageCalculator coverageCalculator)
      throws IOException {
    for (String band : collection.bandNames()) {
      String bandPath = tile.bandPath(band);
      GetObjectRequest objectRequest = new GetObjectRequest(collection.getS3Bucket(), bandPath);

      try (S3ObjectInputStream is = s3Service.getObject(objectRequest).getObjectContent()) {
        coverageCalculator.addImage(is);
      }
    }
  }
}
