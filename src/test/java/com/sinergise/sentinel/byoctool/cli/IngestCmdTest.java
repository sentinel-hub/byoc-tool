package com.sinergise.sentinel.byoctool.cli;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class IngestCmdTest {

  @ParameterizedTest
  @MethodSource("goodTiles")
  void goodTilesUsingDefaultTilePattern(String tile) {
    assertTrue(Pattern.compile(IngestCmd.DEFAULT_FILE_PATTERN).matcher(tile).matches());
  }

  private static Stream<String> goodTiles() {
    return Stream.of(
        "tile/band.tiff",
        "tile/band.TIFF",
        "tile/band.tif",
        "tile/band.TIF",
        "tile/band.jp2",
        "tile/band.JP2");
  }
}
