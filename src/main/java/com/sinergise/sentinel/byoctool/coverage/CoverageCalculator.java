package com.sinergise.sentinel.byoctool.coverage;

import com.sinergise.sentinel.byoctool.cli.CoverageTracingConfig;
import com.sinergise.sentinel.byoctool.tiff.TiffCompoundDirectory;
import com.sinergise.sentinel.byoctool.tiff.TiffDirectory.Scale;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReader;
import com.twelvemonkeys.imageio.plugins.tiff.TIFFImageReaderSpi;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.imageio.stream.ImageInputStream;

import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryCollection;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.PrecisionModel;
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
      coveragesIntersection = removeSmallParts(coveragesIntersection,
          lowestResolution * lowestResolution,
          new GeometryFactory(new PrecisionModel(), coveragesIntersection.getSRID()));
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

  static Geometry removeSmallParts(Geometry geometry, double areaThreshold, GeometryFactory geometryFactory) {
    List<Geometry> partsToKeep;
    if (geometry instanceof GeometryCollection) {
      partsToKeep = removeSmallParts((GeometryCollection) geometry, areaThreshold, geometryFactory);
    } else if (geometry instanceof Polygon) {
      partsToKeep = removeSmallParts((Polygon) geometry, areaThreshold, geometryFactory);
    } else {
      partsToKeep = Collections.emptyList();
    }
    switch (partsToKeep.size()) {
      case 0:
        return geometryFactory.createGeometryCollection();
      case 1:
        return partsToKeep.get(0);
      default:
        return geometryFactory.createGeometryCollection(partsToKeep.toArray(new Geometry[0]));
    }
  }

  private static List<Geometry> removeSmallParts(GeometryCollection collection, double areaThreshold,
      GeometryFactory geometryFactory) {
    List<Geometry> partsToKeep = new LinkedList<>();
    for (int i = 0; i < collection.getNumGeometries(); i++) {
      Geometry part = removeSmallParts(collection.getGeometryN(i), areaThreshold, geometryFactory);
      if (part instanceof GeometryCollection) {
        for (int j = 0; j < part.getNumGeometries(); j++) {
          partsToKeep.add(part.getGeometryN(j));
        }
      } else {
        partsToKeep.add(part);
      }
    }
    return partsToKeep;
  }

  private static List<Geometry> removeSmallParts(Polygon polygon, double areaThreshold,
      GeometryFactory geometryFactory) {
    if (polygon.getArea() < areaThreshold) {
      return Collections.emptyList();
    }
    if (polygon.getNumInteriorRing() > 0) {
      List<LineString> interiorRingsToKeep = new LinkedList<>();
      for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
        LineString interiorRing = polygon.getInteriorRingN(i);
        double interiorArea = new GeometryFactory().createPolygon(interiorRing.getCoordinates()).getArea();
        if (interiorArea >= areaThreshold) {
          interiorRingsToKeep.add(interiorRing);
        }
      }
      if (interiorRingsToKeep.size() < polygon.getNumInteriorRing()) {
        polygon = new GeometryFactory().createPolygon(
                geometryFactory.createLinearRing(polygon.getExteriorRing().getCoordinates()),
                interiorRingsToKeep.stream()
                        .map(ring -> geometryFactory.createLinearRing(ring.getCoordinates()))
                        .toArray(LinearRing[]::new));
      }
    }
    return Collections.singletonList(polygon);
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
