package com.sinergise.sentinel.byoctool.cli;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch.FileMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class IngestCmdTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "tile/band.tiff",
      "tile/band.TIFF",
      "tile/band.tif",
      "tile/band.TIF",
      "tile/band.jp2",
      "tile/band.JP2"
  })
  void testDefaultTilePattern(String tile) {
    assertTrue(Pattern.compile(IngestCmd.DEFAULT_FILE_PATTERN).matcher(tile).matches());
  }

  @Test
  void testSingleResampling() {
    IngestCmd ingestCmd = new IngestCmd();
    ingestCmd.resampling = new String[]{"average"};

    List<FileMap> fileMaps = Arrays.asList(
        new FileMap(null, Arrays.asList(
            new BandMap(1, "B1"),
            new BandMap(2, "B2"))),
        new FileMap(null, Collections.singletonList(
            new BandMap(1, "B3")))
    );

    ingestCmd.setResampling(fileMaps);

    assertEquals("average", fileMaps.get(0).bands().get(0).resampling());
    assertEquals("average", fileMaps.get(0).bands().get(1).resampling());
    assertEquals("average", fileMaps.get(1).bands().get(0).resampling());
  }

  @Test
  void testMultipleResampling() {
    IngestCmd ingestCmd = new IngestCmd();
    ingestCmd.resampling = new String[]{"average", "average", "mode"};

    List<FileMap> fileMaps = Arrays.asList(
        new FileMap(null, Arrays.asList(
            new BandMap(1, "B1"),
            new BandMap(2, "B2"))),
        new FileMap(null, Collections.singletonList(
            new BandMap(1, "B3")))
    );

    ingestCmd.setResampling(fileMaps);

    assertEquals("average", fileMaps.get(0).bands().get(0).resampling());
    assertEquals("average", fileMaps.get(0).bands().get(1).resampling());
    assertEquals("mode", fileMaps.get(1).bands().get(0).resampling());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "average,mode",
      "average,average,average,mode"
  })
  void testIncorrectResampling(String resampling) {
    IngestCmd ingestCmd = new IngestCmd();
    ingestCmd.resampling = resampling.split(",");

    List<FileMap> fileMaps = Arrays.asList(
        new FileMap(null, Arrays.asList(
            new BandMap(1, "B"),
            new BandMap(2, "G"))),
        new FileMap(null, Collections.singletonList(
            new BandMap(1, "MASK")))
    );

    assertThrows(IllegalArgumentException.class,
        () -> ingestCmd.setResampling(fileMaps));
  }
}
