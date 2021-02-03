package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.tiff.TiffCompoundDirectory;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory;

import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

public class TileValidation {

  static Collection<String> validate(Collection<Path> paths) throws IOException {
    if (paths.isEmpty()) {
      return Collections.emptyList();
    }

    List<String> errors = new LinkedList<>();

    List<TiffCompoundDirectory> ifds = new LinkedList<>();

    for (Path path : paths) {
      try (ImageInputStream iis = ImageIO.createImageInputStream(path.toFile())) {
        TiffCompoundDirectory ifd = new TiffCompoundDirectory(iis);
        ifds.add(ifd);

        if (ifd.geoAsciiParams() == null) {
          errors.add(missingGeoParams(path));
        }

        if (ifd.modelTiePoint() == null) {
          errors.add(missingTiePoint(path));
        }

        if (ifd.scale() == null) {
          errors.add(missingScale(path));
        }
      }
    }

    if (differentValues(ifds, TiffDirectory::geoAsciiParams)) {
      errors.add(differentGeoParams());
    } else {
      Integer epsgCode = GdalSrsInfo.readEpsgCode(paths.iterator().next());

      if (epsgCode == null || !isCrsSupported(epsgCode)) {
        errors.add(unsupportedEpsgCode(epsgCode));
      }
    }

    if (differentValues(ifds, TiffDirectory::modelTiePoint)) {
      errors.add(differentTiePoints());
    }

    return errors;
  }

  private static String missingGeoParams(Path path) {
    return String.format(
        "File %s is missing TIFF tag %s, which is required so we can get coordinate reference system.",
        path, TiffDirectory.TAG_GEO_ASCII_PARAMS);
  }

  private static String missingTiePoint(Path path) {
    return String.format(
        "File %s is missing TIFF tag %s, which is required so we can get location.",
        path, TiffDirectory.TAG_MODEL_TIE_POINT);
  }

  private static String missingScale(Path path) {
    return String.format(
        "File %s is missing TIFF tag %s, which is required so we can get pixel size.",
        path, TiffDirectory.TAG_MODEL_PIXEL_SCALE);
  }

  private static String differentGeoParams() {
    return String.format(
        "Files have different values in TIFF tag %d.", TiffDirectory.TAG_GEO_ASCII_PARAMS);
  }

  private static String differentTiePoints() {
    return String.format(
        "Files have different values in TIFF tag %d.", TiffDirectory.TAG_MODEL_TIE_POINT);
  }

  private static String unsupportedEpsgCode(Integer epsgCode) {
    return String.format("Files has unsupported crs with EPSG code %d.", epsgCode);
  }

  private static <T> boolean differentValues(
      Collection<T> items, Function<T, Object> valueSupplier) {

    return items.stream().map(valueSupplier).distinct().count() >= 2;
  }

  private static boolean isCrsSupported(int epsgCode) {
    return epsgCode == 4326
        || epsgCode == 3857
        || (epsgCode >= 32601 && epsgCode <= 32660)
        || (epsgCode >= 32701 && epsgCode <= 32760);
  }
}
