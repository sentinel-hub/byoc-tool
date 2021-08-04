package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.cli.IngestCmd;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileSearchTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "folder/fileA.tif",
      "folder/fileB.tif",
      "top-folder/folder/fileA.tif"
  })
  void defaultCogStorageFolder(String file) {
    Pattern filePattern = Pattern.compile(IngestCmd.DEFAULT_FILE_PATTERN);
    Matcher matcher = filePattern.matcher(file);
    assertTrue(matcher.matches());

    String folder = TileSearch.replaceRegexGroups(IngestCmd.DEFAULT_COG_STORAGE_FOLDER, matcher);

    assertEquals("folder", folder);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "data/QA/2020-06-01.tif",
      "data/SR/2020-06-01.tif"
  })
  void customCogStorageFolder(String file) {
    Pattern filePattern = Pattern.compile(".*(?<firstPart>data/)(SR|QA)/(?<secondPart>.*(?<year>[0-9]{4})-(?<month>[0-9]{2})-(?<day>[0-9]{2}))\\.tif");
    Matcher matcher = filePattern.matcher(file);
    assertTrue(matcher.matches());

    String folder = TileSearch.replaceRegexGroups("<firstPart><secondPart>", matcher);

    assertEquals("data/2020-06-01", folder);
  }

  @Test
  void detectSensingTime() {
    String file = "top-folder/UTM-24000/33N/15E-212N/PF-SR/2020-06-01.tif";
    Pattern filePattern = Pattern.compile(".*(?<firstPart>UTM.*/)(.*-(SR|QA)/)(?<secondPart>(?<year>[0-9]{4})-(?<month>[0-9]{2})-(?<day>[0-9]{2}).*)");
    Matcher matcher = filePattern.matcher(file);
    assertTrue(matcher.matches());

    Instant sensingTime = TileSearch.getSensingTime(matcher);

    assertEquals(Instant.parse("2020-06-01T00:00:00Z"), sensingTime);
  }
}