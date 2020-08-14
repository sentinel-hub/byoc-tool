package com.sinergise.sentinel.byoctool.coverage;

import com.sinergise.sentinel.byoctool.cli.CoverageTracingConfig;
import com.sinergise.sentinel.byoctool.tiff.TiffCompoundDirectory;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory.Scale;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi;
import java.io.IOException;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.locationtech.jts.operation.buffer.BufferParameters;
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier;

@RequiredArgsConstructor
public class CoverageCalculator {

  private final CoverageTracingConfig config;

  private Geometry coveragesIntersection;
  private Envelope tileEnvelope;
  private Integer epsgCode;
  private Double lowestResolution;

  private static final BufferParameters BUFFER_PARAMETERS =
      new BufferParameters(1, BufferParameters.CAP_SQUARE, BufferParameters.JOIN_MITRE, 10);

  public void addImage(Path path) throws IOException {
    addImage(path.toFile());
  }

  public void addImage(Object input) throws IOException {
    try (ImageInputStream iis = ImageIO.createImageInputStream(input)) {
      iis.mark();
      TiffCompoundDirectory compoundDirectory = new TiffCompoundDirectory(iis);
      iis.reset();

      TIFFImageReader imageReader =
          (TIFFImageReader) new TIFFImageReaderSpi().createReaderInstance();
      imageReader.setInput(iis);

      int imageIndex =
          config.getImageIndex() < compoundDirectory.directoryCount()
              ? config.getImageIndex()
              : compoundDirectory.directoryCount() - 1;
      Geometry geometry = Vectorization.vectorize(imageReader.read(imageIndex), compoundDirectory);

      double resolution = calculateResolution(compoundDirectory, imageIndex);
      if (lowestResolution == null || resolution > lowestResolution) {
        lowestResolution = resolution;
      }

      if (coveragesIntersection == null) {
        coveragesIntersection = geometry;
        this.tileEnvelope = compoundDirectory.envelope();
        this.epsgCode = compoundDirectory.epsgCode();
      } else {
        coveragesIntersection = coveragesIntersection.intersection(geometry);
      }
    }
  }

  public Geometry getCoverage() {
    Geometry coverage;

    if (coveragesIntersection == null) {
      coverage = new GeometryFactory().createPolygon();
    } else {
      coverage = DouglasPeuckerSimplifier.simplify(coveragesIntersection, 0);

      if (config.getNegativeBufferInPixels() != 0) {
        double negativeBuffer = lowestResolution * config.getNegativeBufferInPixels();

        Geometry tile = new GeometryFactory().toGeometry(tileEnvelope);
        Geometry coverageInverse = tile.difference(coverage);
        Geometry inverseBuf = BufferOp.bufferOp(coverageInverse, negativeBuffer, BUFFER_PARAMETERS);
        inverseBuf = DouglasPeuckerSimplifier.simplify(inverseBuf, 0);
        coverage = coverage.difference(inverseBuf);
      }

      if (config.getDistanceToleranceInPixels() != 0) {
        double distanceTolerance = lowestResolution * config.getDistanceToleranceInPixels();

        coverage = DouglasPeuckerSimplifier.simplify(coverage, distanceTolerance);
      }
    }

    coverage.setSRID(epsgCode);

    return coverage;
  }

  private double calculateResolution(TiffCompoundDirectory compoundDirectory, int imageIndex) {
    Scale scale = compoundDirectory.scale();
    double resolution = ((scale.x() + scale.y()) / 2);

    if (imageIndex == 0) {
      return resolution;
    }

    int mainImageHeight = Math.toIntExact(compoundDirectory.directory(0).imageHeight());
    int overviewHeight = Math.toIntExact(compoundDirectory.directory(imageIndex).imageHeight());

    return resolution * mainImageHeight / (float) overviewHeight;
  }
}
