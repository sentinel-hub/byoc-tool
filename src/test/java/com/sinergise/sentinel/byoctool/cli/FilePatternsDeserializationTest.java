package com.sinergise.sentinel.byoctool.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.ingestion.TileSearch.FileMap;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilePatternsDeserializationTest {

  @Test
  void singleComponent() {
    List<FileMap> items = FileMapsDeserialization.deserialize(Collections.singleton("TIF;2:G"));

    assertEquals(1, items.size());
    assertEquals(1, items.get(0).bands().size());
    assertEquals(2, items.get(0).bands().get(0).index());
    assertEquals("G", items.get(0).bands().get(0).name());
  }

  @Test
  void multipleComponents() {
    List<FileMap> items = FileMapsDeserialization.deserialize(Collections.singleton("TIF;1:R;2:G"));

    assertEquals(1, items.size());

    FileMap fp = items.get(0);
    assertEquals(2, fp.bands().size());

    BandMap firstBand = fp.bands().get(0);
    assertEquals(1, firstBand.index());
    assertEquals("R", firstBand.name());

    BandMap secondBand = fp.bands().get(1);
    assertEquals(2, secondBand.index());
    assertEquals("G", secondBand.name());
  }

  @Test
  void supportWhitespace() {
    List<FileMap> items = FileMapsDeserialization.deserialize(Collections.singleton("TIF; 1 : R "));

    assertEquals(1, items.size());
    assertEquals(1, items.get(0).bands().size());
    assertEquals(1, items.get(0).bands().get(0).index());
    assertEquals("R", items.get(0).bands().get(0).name());
  }
}
