package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.InputFile;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.ingestion.FileFinder.Match;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class TileSearch {

  private static final String YEAR_CAPTURE_GROUP = "year";
  private static final String MONTH_CAPTURE_GROUP = "month";
  private static final String DAY_CAPTURE_GROUP = "day";
  private static final String HOUR_CAPTURE_GROUP = "hour";
  private static final String MINUTE_CAPTURE_GROUP = "minute";
  private static final String SECOND_CAPTURE_GROUP = "second";
  private static final String SUB_SECOND_CAPTURE_GROUP = "subsecond";

  public static Collection<Tile> search(
      Path start, Pattern tilePattern, Collection<FileMap> fileMaps) throws IOException {

    Map<String, Tile> tiles = new HashMap<>();

    for (Match m : FileFinder.find(start, tilePattern)) {
      Path file = m.file();
      String tileGroup = m.matcher().group("tile");
      String tilePath = toForwardSlashes(tileGroup);

      Tile tile = tiles.getOrDefault(tilePath, null);
      if (tile == null) {
        tile = new Tile(tilePath, sensingTime(m.matcher()), null, new LinkedList<>());
        tiles.put(tilePath, tile);
      }

      for (FileMap fm : fileMaps) {
        if (!fm.filePattern().matcher(file.toString()).find()) {
          continue;
        }

        final List<BandMap> bands;
        if (!fm.bands().isEmpty()) {
          bands = fm.bands();
        } else {
          bands = Collections.singletonList(new BandMap(1, fileNameWithoutExtension(file)));
        }

        tile.inputFiles().add(new InputFile(file, bands));
        break;
      }
    }

    return tiles.values();
  }

  private static String fileNameWithoutExtension(Path file) {
    String fileName = file.getFileName().toString();
    return fileName.substring(0, fileName.lastIndexOf("."));
  }

  private static String toForwardSlashes(String str) {
    return str.replaceAll("\\\\", "/");
  }

  private static Instant sensingTime(Matcher m) {
    String pattern = m.pattern().pattern();

    if (!pattern.contains(YEAR_CAPTURE_GROUP)) {
      return null;
    }

    int year = Integer.parseInt(m.group(YEAR_CAPTURE_GROUP));
    int month = captureGroupAsInt(m, pattern, MONTH_CAPTURE_GROUP).orElse(1);
    int day = captureGroupAsInt(m, pattern, DAY_CAPTURE_GROUP).orElse(1);
    int hour = captureGroupAsInt(m, pattern, HOUR_CAPTURE_GROUP).orElse(0);
    int minute = captureGroupAsInt(m, pattern, MINUTE_CAPTURE_GROUP).orElse(0);
    int second = captureGroupAsInt(m, pattern, SECOND_CAPTURE_GROUP).orElse(0);
    int subsecond = captureGroupAsInt(m, pattern, SUB_SECOND_CAPTURE_GROUP).orElse(0);

    return LocalDateTime.of(year, month, day, hour, minute, second, subsecond).toInstant(ZoneOffset.UTC);
  }

  private static Optional<Integer> captureGroupAsInt(Matcher m, String pattern, String group) {
    return pattern.contains(group)
        ? Optional.of(Integer.parseInt(m.group(group)))
        : Optional.empty();
  }

  @Value
  @Accessors(fluent = true)
  public static class FileMap {

    private final Pattern filePattern;
    private final List<BandMap> bands;
  }
}
