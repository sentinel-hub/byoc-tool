package com.sinergise.sentinel.byoctool.tiff;

import static com.twelvemonkeys.imageio.metadata.tiff.TIFF.*;

import com.twelvemonkeys.imageio.metadata.Directory;
import com.twelvemonkeys.imageio.metadata.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Value;
import lombok.experimental.Accessors;
import org.locationtech.jts.geom.Envelope;

public class TiffDirectory {

  public static final int TAG_MODEL_PIXEL_SCALE = 33550;
  public static final int TAG_GEO_ASCII_PARAMS = 34737;
  public static final int TAG_MODEL_TIE_POINT = 33922;
  public static final int TAG_GDAL_NO_DATA_VALUE = 42113;

  private static final Pattern GEO_TIFF_CRS_PATTERN =
      Pattern.compile("(?:WGS 84 / (UTM|Pseudo-Mercator)(?: zone ([0-9]{2})([SN]))?\\|)?WGS 84\\|");

  private final Directory directory;

  public TiffDirectory(Directory directory) {
    this.directory = directory;
  }

  public Long imageWidth() {
    Entry entry = getEntry(TAG_IMAGE_WIDTH);

    if (entry == null) {
      return null;
    }

    return ((Number) entry.getValue()).longValue();
  }

  public Long imageHeight() {
    Entry entry = getEntry(TAG_IMAGE_HEIGHT);

    if (entry == null) {
      return null;
    }

    return ((Number) entry.getValue()).longValue();
  }

  public int sampleFormat() {
    Entry entry = getEntry(TAG_SAMPLE_FORMAT);

    if (entry == null) {
      return SampleFormat.UINT;
    }

    return (Integer) entry.getValue();
  }

  public Scale scale() {
    Entry entry = getEntry(TAG_MODEL_PIXEL_SCALE);

    if (entry == null) {
      return null;
    }

    return new Scale((double[]) entry.getValue());
  }

  public String noDataValue() {
    Entry entry = getEntry(TAG_GDAL_NO_DATA_VALUE);

    if (entry == null) {
      return null;
    }

    return entry.getValueAsString();
  }

  public Integer noDataValueAsInt() {
    String noDataValue = noDataValue();

    if (noDataValue == null) {
      return null;
    }

    return Integer.parseInt(noDataValue);
  }

  public TiePoint modelTiePoint() {
    Entry entry = getEntry(TAG_MODEL_TIE_POINT);

    if (entry == null) {
      return null;
    }

    return new TiePoint((double[]) entry.getValue());
  }

  public String geoAsciiParams() {
    Entry entry = getEntry(TAG_GEO_ASCII_PARAMS);

    if (entry == null) {
      return null;
    }

    return entry.getValueAsString();
  }

  public Envelope envelope() {
    Scale scale = scale();
    TiePoint tiePoint = modelTiePoint();

    double minX = tiePoint.x();
    double maxY = tiePoint.y();
    double maxX = minX + scale.x() * imageWidth();
    double minY = maxY - scale.y() * imageHeight();

    return new Envelope(minX, maxX, minY, maxY);
  }

  public Integer epsgCode() {
    return epsgCode(geoAsciiParams());
  }

  static Integer epsgCode(String geoParams) {
    Matcher matcher = GEO_TIFF_CRS_PATTERN.matcher(geoParams);

    if (matcher.matches()) {
      if (matcher.group(1) == null) {
        return 4326;
      }

      if (matcher.group(1).equals("Pseudo-Mercator")) {
        return 3857;
      }

      if (matcher.group(1).equals("UTM")) {
        int zone = Integer.parseInt(matcher.group(2));

        String sn = matcher.group(3);
        if (sn.equals("N")) {
          return 32600 + zone;
        } else if (sn.equals("S")) {
          return 32700 + zone;
        }
      }
    }

    return null;
  }

  private Entry getEntry(int entryId) {
    return directory.getEntryById(entryId);
  }

  public static class SampleFormat {

    public static final int UINT = 1;
    public static final int INT = 2;
    public static final int IEEEFP = 3;
  }

  @Value
  @Accessors(fluent = true)
  public static class Scale {

    private final double x;
    private final double y;

    Scale(double[] data) {
      this.x = data[0];
      this.y = data[1];
    }
  }

  @Value
  @Accessors(fluent = true)
  public static class TiePoint {

    private final double x;
    private final double y;

    TiePoint(double[] data) {
      this.x = data[3];
      this.y = data[4];
    }
  }
}
