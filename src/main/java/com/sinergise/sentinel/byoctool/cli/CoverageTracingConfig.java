package com.sinergise.sentinel.byoctool.cli;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import picocli.CommandLine.Option;

@Getter
@Setter
@Accessors(chain = true)
public class CoverageTracingConfig {

  @Option(
      names = {"--trace-image-idx"},
      description =
          "Index of an image to trace. At index 0 is the main image, while the rest are overviews. Overviews are sorted by resolution, where the overview at index 1 has highest resolution and the overview at the last index has lowest resolution. If a file has less images, then the last image is used. The default is ${DEFAULT-VALUE}.",
      defaultValue = "0")
  private int imageIndex;

  @Option(
      names = {"--negative-buffer"},
      description =
          "Sets the amount of negative buffer to apply to coverage geometries right before they are simplified, in pixels. Values should be positive or zero. The pixel size is represented by the lowest resolution band at the set image index (see --trace-image-idx). If set to zero, the coverage may be simplified in such way that it includes no-data pixels outside the boundaries, however zero will also prevent gaps between touching but not intersecting tiles in a collection. The default is ${DEFAULT-VALUE}. To use, make sure --trace-coverage is set. Also see --distance-tolerance.",
      defaultValue = "10")
  private double negativeBufferInPixels;

  @Option(
      names = {"--distance-tolerance"},
      description =
          "Sets the distance tolerance in pixels for coverage simplification using the Ramer–Douglas–Peucker algorithm. The pixel size is represented by the lowest resolution band at the set image index (see --trace-image-idx). Traced geometries with more than 100 points will be rejected, in which case the tile will not be processed further. Increasing the distance tolerance may help as it will reduce the number of points. The default is ${DEFAULT-VALUE}. To use, make sure --trace-coverage is set. Also see --negative-buffer.",
      defaultValue = "10")
  private double distanceToleranceInPixels;
}
