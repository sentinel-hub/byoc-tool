package byoc.ingestion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GdalSrsInfo {

  private static final Pattern EPSG_GDAL_SRS_INFO = Pattern.compile("EPSG:(\\d+)");

  static Integer readEpsgCode(Path file) {
    String[] command = {"gdalsrsinfo", "-e", file.toString()};
    StringBuilder sb = new StringBuilder();

    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      Process process = pb.start();

      try (BufferedReader rdr =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        String s;
        while ((s = rdr.readLine()) != null) {
          sb.append(s);
        }
      }

      process.waitFor();
      int exitValue = process.exitValue();
      if (exitValue != 0) {
        throw new RuntimeException(
            String.format(
                "exit value = %d, while running \"%s\"", exitValue, String.join(" ", command)));
      }
      process.destroy();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Running command %s failed!", String.join(" ", command)), e);
    }

    Matcher matcher = EPSG_GDAL_SRS_INFO.matcher(sb.toString());
    if (matcher.find() && matcher.groupCount() == 1) {
      return Integer.parseInt(matcher.group(1));
    }

    return null;
  }
}
