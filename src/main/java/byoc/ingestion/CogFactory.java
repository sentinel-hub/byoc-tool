package byoc.ingestion;

import byoc.ingestion.ByocIngestor.BandSource;
import byoc.ingestion.ByocIngestor.Tile;
import byoc.tiff.TiffCompoundDirectory;
import byoc.tiff.TiffDirectory.SampleFormat;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import lombok.Setter;
import lombok.experimental.Accessors;

@Setter
@Accessors(chain = true)
public class CogFactory {

  private Integer noDataValue;
  private Boolean useCompressionPredictor = true;
  private Path processingFolder;

  Path createCog(Tile tile, Path inputFile, BandSource bandSource) throws IOException {
    Path intermediateFile = getIntermediateFile(tile, inputFile);
    Path outputFile = getOutputFile(tile, inputFile, intermediateFile, bandSource);

    try {
      createGeoTiff(inputFile, bandSource.index(), noDataValue, intermediateFile);
      addOverviews(intermediateFile);
      addTiling(intermediateFile, useCompressionPredictor, outputFile);

      return outputFile;
    } finally {
      Files.deleteIfExists(intermediateFile);
    }
  }

  private Path getIntermediateFile(Tile tile, Path inputFile) {
    final Path folder;
    final String nameStart;
    String inputFileName = stripSuffix(inputFile.getFileName().toString());

    if (processingFolder == null) {
      folder = inputFile.getParent();
      nameStart = inputFileName;
    } else {
      folder = processingFolder;
      nameStart = getTilePathWithUnderscores(tile) + "_" + inputFileName;
    }

    while (true) {
      Path intermediate =
          folder.resolve(nameStart + "_intermediate_" + System.nanoTime() + ".tiff");
      if (!Files.exists(intermediate)) {
        return intermediate;
      }
    }
  }

  private Path getOutputFile(
      Tile tile, Path inputFile, Path intermediateFile, BandSource bandSource) {
    final String nameStart;

    if (processingFolder == null) {
      nameStart = stripSuffix(inputFile.getFileName().toString());
    } else {
      nameStart = getTilePathWithUnderscores(tile);
    }

    return intermediateFile
        .getParent()
        .resolve(String.format("%s_%s.tiff", nameStart, bandSource.name()));
  }

  private static String stripSuffix(String fileName) {
    return fileName.substring(0, fileName.lastIndexOf('.'));
  }

  private String getTilePathWithUnderscores(Tile tile) {
    return tile.path().replace(File.separatorChar, '_');
  }

  private static void createGeoTiff(
      Path inputPath, int bandNumber, Integer noDataValue, Path outPath) {
    List<String> command =
        new LinkedList<>(
            Arrays.asList("gdal_translate", "-of", "GTIFF", "-b", String.valueOf(bandNumber)));

    if (noDataValue != null) {
      command.addAll(Arrays.asList("-a_nodata", String.valueOf(noDataValue)));
    }

    command.addAll(
        Arrays.asList(inputPath.toAbsolutePath().toString(), outPath.toAbsolutePath().toString()));

    runCommand(command.toArray(new String[0]));
  }

  private static void addOverviews(Path inputPath) {
    runCommand(
        "gdaladdo",
        "-r",
        "average",
        "--config",
        "GDAL_TIFF_OVR_BLOCKSIZE",
        "1024",
        inputPath.toAbsolutePath().toString(),
        "-minsize",
        "1024");
  }

  private static void addTiling(Path inputPath, boolean compressionPredictor, Path outputPath)
      throws IOException {
    List<String> command =
        new LinkedList<>(
            Arrays.asList(
                "gdal_translate",
                "-co",
                "TILED=YES",
                "-co",
                "COPY_SRC_OVERVIEWS=YES",
                "--config",
                "GDAL_TIFF_OVR_BLOCKSIZE",
                "1024",
                "-co",
                "BLOCKXSIZE=1024",
                "-co",
                "BLOCKYSIZE=1024",
                "-co",
                "COMPRESS=DEFLATE"));

    if (compressionPredictor) {
      try (InputStream is = Files.newInputStream(inputPath)) {
        ImageInputStream iis = ImageIO.createImageInputStream(is);
        TiffCompoundDirectory directory = new TiffCompoundDirectory(iis);
        Integer predictor = getPredictor(directory.sampleFormat());

        if (predictor != null) {
          command.addAll(Arrays.asList("-co", "PREDICTOR=" + predictor));
        }
      }
    }

    command.addAll(
        Arrays.asList(
            inputPath.toAbsolutePath().toString(), outputPath.toAbsolutePath().toString()));

    runCommand(command.toArray(new String[0]));
  }

  private static Integer getPredictor(int sampleFormat) {
    final Integer predictor;
    if (sampleFormat == SampleFormat.UINT || sampleFormat == SampleFormat.INT) {
      predictor = 2;
    } else if (sampleFormat == SampleFormat.IEEEFP) {
      predictor = 3;
    } else {
      predictor = null;
    }
    return predictor;
  }

  private static void runCommand(String... command) {
    try {
      ProcessBuilder pb = new ProcessBuilder(command);
      Process process = pb.start();

      List<String> errorLines = new ArrayList<>();
      try (BufferedReader rdr =
          new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
        String s;
        while ((s = rdr.readLine()) != null) {
          errorLines.add(s);
        }
      }

      process.waitFor();
      int exitValue = process.exitValue();
      if (exitValue != 0) {
        throw new RuntimeException(
            String.format(
                "exit value = %d, while running \"%s\". output:\n%s",
                exitValue, String.join(" ", command), String.join("\n", errorLines)));
      }
      process.destroy();
    } catch (Exception e) {
      throw new RuntimeException(
          String.format("Running command %s failed!", String.join(" ", command)), e);
    }
  }
}
