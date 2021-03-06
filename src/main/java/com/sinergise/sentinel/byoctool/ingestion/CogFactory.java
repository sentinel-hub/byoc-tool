package com.sinergise.sentinel.byoctool.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.BandMap;
import com.sinergise.sentinel.byoctool.ingestion.ByocIngestor.Tile;
import com.sinergise.sentinel.byoctool.ingestion.GdalInfo.Band;
import com.sinergise.sentinel.byoctool.tiff.TiffCompoundDirectory;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory.SampleFormat;
import lombok.Setter;
import lombok.experimental.Accessors;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Setter
@Accessors(chain = true)
public class CogFactory {

  private Integer noDataValue;

  private Boolean useCompressionPredictor = true;

  private Path processingFolder;

  Path createCog(Tile tile, Path inputFile, BandMap bandMap) throws IOException {
    Path intermediateFile = getIntermediateFile(tile, inputFile);
    Path outputFile = getOutputFile(tile, inputFile, intermediateFile, bandMap);

    try {
      GdalInfo gdalInfo = getGdalInfo(inputFile);
      String dataType =
          gdalInfo.getBands().stream()
              .filter(band -> bandMap.index() == band.getBand())
              .findFirst()
              .map(Band::getType)
              .orElse(null);

      createGeoTiff(inputFile, bandMap.index(), noDataValue, dataType, intermediateFile);
      addOverviews(intermediateFile, bandMap);
      addTiling(intermediateFile, useCompressionPredictor, outputFile);
      runChecksum(outputFile);

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

  private Path getOutputFile(Tile tile, Path inputFile, Path intermediateFile, BandMap bandMap) {
    final String nameStart;

    if (processingFolder == null) {
      nameStart = stripSuffix(inputFile.getFileName().toString());
    } else {
      nameStart = getTilePathWithUnderscores(tile);
    }

    return intermediateFile
        .getParent()
        .resolve(String.format("%s_%s.tiff", nameStart, bandMap.name()));
  }

  private static String stripSuffix(String fileName) {
    return fileName.substring(0, fileName.lastIndexOf('.'));
  }

  private String getTilePathWithUnderscores(Tile tile) {
    return tile.path().replace('/', '_');
  }

  private GdalInfo getGdalInfo(Path inputFile) throws JsonProcessingException {
    ProcessBuilder pb = new ProcessBuilder("gdalinfo", "-json", inputFile.toString());
    String output = ProcessUtil.runCommand(pb);
    return new ObjectMapper().readValue(output, GdalInfo.class);
  }

  private static void createGeoTiff(
      Path inputPath, int bandNumber, Integer noDataValue, String dataType, Path outPath) {
    List<String> command =
        new LinkedList<>(
            Arrays.asList("gdal_translate", "-of", "GTIFF", "-co", "BIGTIFF=YES", "-b", String.valueOf(bandNumber)));

    if (noDataValue != null) {
      command.addAll(Arrays.asList("-a_nodata", String.valueOf(noDataValue)));
    }

    if (dataType != null) {
      command.addAll(Arrays.asList("-ot", dataType));
    }

    command.addAll(
        Arrays.asList(inputPath.toAbsolutePath().toString(), outPath.toAbsolutePath().toString()));

    ProcessUtil.runCommand(command.toArray(new String[0]));
  }

  private static void addOverviews(Path inputPath, BandMap bandMap) {
    String resampling = Optional.ofNullable(bandMap.resampling())
        .orElse("average");

    List<String> cmd =
        new LinkedList<>(
            Arrays.asList(
                "gdaladdo",
                "-r",
                resampling,
                "--config",
                "GDAL_TIFF_OVR_BLOCKSIZE",
                "1024",
                inputPath.toAbsolutePath().toString()));

    if (bandMap.overviewLevels() != null) {
      for (double level : bandMap.overviewLevels()) {
        cmd.add(String.valueOf(level));
      }
    } else {
      Integer minSize = Optional.ofNullable(bandMap.minSize()).orElse(512);
      cmd.addAll(Arrays.asList("-minsize", minSize.toString()));
    }

    ProcessUtil.runCommand(cmd.toArray(new String[0]));
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
      try (ImageInputStream imageStream = ImageIO.createImageInputStream(inputPath.toFile())) {
        TiffCompoundDirectory directory = new TiffCompoundDirectory(imageStream);
        Integer predictor = getPredictor(directory.sampleFormat());

        if (predictor != null) {
          command.addAll(Arrays.asList("-co", "PREDICTOR=" + predictor));
        }
      }
    }

    command.addAll(
        Arrays.asList(
            inputPath.toAbsolutePath().toString(), outputPath.toAbsolutePath().toString()));

    List<String> commandStdTiff = new ArrayList<>(command);
    List<String> commandBigTiff = new ArrayList<>(command);
    commandStdTiff.addAll(Arrays.asList("-co", "BIGTIFF=NO"));
    commandBigTiff.addAll(Arrays.asList("-co", "BIGTIFF=YES"));

    try {
      ProcessUtil.runCommand(commandStdTiff.toArray(new String[0]));
    } catch (RuntimeException e) {
      ProcessUtil.runCommand(commandBigTiff.toArray(new String[0]));
    }
  }

  static void runChecksum(Path outputFile) {
    String output = ProcessUtil.runCommand("gdalinfo", "-checksum", outputFile.toString());

    if (output.toLowerCase().contains("checksum value could not be computed")) {
      throw new RuntimeException("Checksum failed to be computed for file: " + outputFile);
    }
  }

  static Integer getPredictor(int sampleFormat) {
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
}
