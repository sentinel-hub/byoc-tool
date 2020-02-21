package byoc.cli;

import byoc.ingestion.ByocIngestor.BandSource;
import byoc.ingestion.TileSearch.FileMap;
import java.util.*;
import java.util.regex.Pattern;

class FileMapsDeserialization {

  private static final Pattern BAND_NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

  static List<FileMap> deserialize(Collection<String> serializedFileMaps) {
    List<FileMap> fileMaps = new LinkedList<>();

    if (serializedFileMaps == null) {
      return fileMaps;
    }

    for (String sfm : serializedFileMaps) {
      String[] parts = sfm.split(";");
      Pattern filePattern = Pattern.compile(parts[0]);
      List<BandSource> bandSources = new LinkedList<>();

      if (parts.length > 1) {
        for (int i = 1; i < parts.length; i++) {
          String component = parts[i];
          String[] bandTuple = component.split(":");

          if (bandTuple.length != 2) {
            throw new RuntimeException(
                String.format(
                    "File mapping \"%s\" is not specified as <ComponentIndex>:<BandName>.",
                    component));
          }

          int index = Integer.parseInt(bandTuple[0].trim());
          String name = bandTuple[1].trim();

          if (!isValidBandName(name)) {
            throw new RuntimeException(
                String.format("Invalid band name \"%s\" in file mapping \"%s\"", component, sfm));
          }

          bandSources.add(new BandSource(index, name));
        }
      } else if (isValidBandName(filePattern.pattern())) {
        bandSources.add(new BandSource(1, filePattern.pattern()));
      }

      fileMaps.add(new FileMap(filePattern, bandSources));
    }

    return fileMaps;
  }

  private static boolean isValidBandName(String bandName) {
    return BAND_NAME_PATTERN.matcher(bandName).matches();
  }
}
