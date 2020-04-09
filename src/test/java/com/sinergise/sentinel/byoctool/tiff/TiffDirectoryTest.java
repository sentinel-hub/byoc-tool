package com.sinergise.sentinel.byoctool.tiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TiffDirectoryTest {

  @ParameterizedTest
  @ValueSource(strings = {"WGS 84|"})
  void crsEpsg4326(String geoAsciiParams) {
    assertEquals(4326, TiffDirectory.epsgCode(geoAsciiParams));
  }

  @ParameterizedTest
  @ValueSource(strings = {"WGS_1984_World_Mercator|WGS 84|"})
  void badCrs(String geoAsciiParams) {
    assertNull(TiffDirectory.epsgCode(geoAsciiParams));
  }
}
