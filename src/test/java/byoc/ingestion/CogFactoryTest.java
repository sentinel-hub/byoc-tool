package byoc.ingestion;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import byoc.ingestion.ByocIngestor.BandMap;
import byoc.tiff.TiffDirectory.SampleFormat;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CogFactoryTest {

  @Test
  void predictor() {
    assertEquals(2, CogFactory.getPredictor(SampleFormat.INT));
    assertEquals(2, CogFactory.getPredictor(SampleFormat.UINT));
    assertEquals(3, CogFactory.getPredictor(SampleFormat.IEEEFP));
  }

  @ParameterizedTest
  @CsvSource({
    "1,R,output_R.tiff",
    "2,G,output_G.tiff",
    "3,B,output_B.tiff",
    "4,dataMask,output_dataMask.tiff"
  })
  void cog(int bandIndex, String bandName, String expectedCogName)
      throws IOException, URISyntaxException {
    Path inputFile = Paths.get(getClass().getResource("input.tiff").toURI());
    Path expectedCog = Paths.get(getClass().getResource(expectedCogName).toURI());

    Path actualCog = new CogFactory().createCog(null, inputFile, new BandMap(bandIndex, bandName));

    assertArrayEquals(Files.readAllBytes(expectedCog), Files.readAllBytes(actualCog));
  }
}
