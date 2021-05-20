package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory.SampleFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CogFactoryTest {

  @Test
  void testGetPredictor() {
    assertEquals(2, CogFactory.getPredictor(SampleFormat.INT));
    assertEquals(2, CogFactory.getPredictor(SampleFormat.UINT));
    assertEquals(3, CogFactory.getPredictor(SampleFormat.IEEEFP));
  }

  @ParameterizedTest
  @CsvSource({
      "1,output_R.tiff",
      "2,output_G.tiff",
      "3,output_B.tiff",
      "4,output_dataMask.tiff"
  })
  void testMultiBandFile(int bandIndex, String expectedCogResource) {
    BandMap bandMap = new BandMap(bandIndex, "band").setMinSize(1024);

    runCogTest(
        "multi_band_file/input.tiff",
        "multi_band_file/" + expectedCogResource,
        bandMap);
  }

  @Test
  void testCustomResampling() {
    BandMap bandMap = new BandMap(1, "band")
        .setResampling("mode")
        .setMinSize(128);

    runCogTest(
        "mode_interpolation/input.tiff",
        "mode_interpolation/output.tiff",
        bandMap);
  }

  private void runCogTest(String inputResource, String outputResource, BandMap bandMap) {
    try {
      Path inputFile = Paths.get(getClass().getResource(inputResource).toURI());
      Path actualCog = new CogFactory().createCog(null, inputFile, bandMap);
      Path expectedCog = Paths.get(getClass().getResource(outputResource).toURI());
      assertArrayEquals(Files.readAllBytes(expectedCog), Files.readAllBytes(actualCog));
    } catch (URISyntaxException | IOException e) {
      throw new RuntimeException(e);
    }
  }
}
