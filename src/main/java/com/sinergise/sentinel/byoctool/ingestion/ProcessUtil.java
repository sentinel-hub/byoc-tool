package com.sinergise.sentinel.byoctool.ingestion;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

public class ProcessUtil {

  public static String runCommand(String... args) {
    return runCommand(new ProcessBuilder(args));
  }

  public static String runCommand(ProcessBuilder pb) {
    try {
      pb.redirectErrorStream(true);
      Process process = pb.start();

      final String stdOut;
      try (BufferedReader rdr =
          new BufferedReader(new InputStreamReader(process.getInputStream()))) {
        stdOut = rdr.lines().collect(Collectors.joining(System.lineSeparator()));
      }

      int errCode = process.waitFor();
      if (errCode != 0) {
        throw new RuntimeException(stdOut);
      }

      return stdOut;
    } catch (Exception e) {
      throw new RuntimeException("Failed to run command " + String.join(" ", pb.command()), e);
    }
  }
}
