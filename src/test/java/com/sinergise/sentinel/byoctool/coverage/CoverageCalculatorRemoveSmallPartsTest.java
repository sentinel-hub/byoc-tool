package com.sinergise.sentinel.byoctool.coverage;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverageCalculatorRemoveSmallPartsTest {

  private GeometryFactory geometryFactory = new GeometryFactory();

  private static final double AREA_THRESHOLD = 1.0;

  @Test
  void pointRemoved() {
    Geometry point = geometryFactory.createPoint(new Coordinate(0.0, 0.0));
    assertTrue(CoverageCalculator.removeSmallParts(point, AREA_THRESHOLD, geometryFactory).isEmpty());
  }

  @Test
  void lineStringRemoved() {
    Geometry lineString = createRing(
        0.0, 0.0,
        1.0, 0.0,
        0.0, 1.0,
        0.0, 0.0);
    assertTrue(CoverageCalculator.removeSmallParts(lineString, AREA_THRESHOLD, geometryFactory).isEmpty());
  }

  @Test
  void smallPolyRemoved() {
    Geometry poly = geometryFactory.createPolygon(
        createRing(0.0, 0.0,
            1.0, 0.0,
            0.0, 1.0,
            0.0, 0.0));
    assertTrue(CoverageCalculator.removeSmallParts(poly, AREA_THRESHOLD, geometryFactory).isEmpty());
  }

  @Test
  void smallHoleRemovedFromPoly() {
    Geometry poly = geometryFactory.createPolygon(
        createRing(0.0, 0.0,
            4.0, 0.0,
            4.0, 4.0,
            0.0, 4.0,
            0.0, 0.0),
        new LinearRing[]{ createRing(1.0, 1.0,
            2.0, 1.0,
            1.0, 2.0,
            1.0, 1.0)});
    assertEquals(16.0, CoverageCalculator.removeSmallParts(poly, AREA_THRESHOLD, geometryFactory).getArea());
  }

  @Test
  void polyWithLargeHoleRetained() {
    Geometry poly = geometryFactory.createPolygon(
        createRing(0.0, 0.0,
            4.0, 0.0,
            4.0, 4.0,
            0.0, 4.0,
            0.0, 0.0),
        new LinearRing[]{ createRing(1.0, 1.0,
            3.0, 1.0,
            3.0, 3.0,
            1.0, 3.0,
            1.0, 1.0)});
    assertEquals(poly, CoverageCalculator.removeSmallParts(poly, AREA_THRESHOLD, geometryFactory));
  }

  @Test
  void collectionWithSingleRetainedFlattened() {
    Geometry collection = geometryFactory.createGeometryCollection(new Geometry[]{
        geometryFactory.createPoint(new Coordinate(0.0, 0.0)),
        geometryFactory.createPolygon(createRing(0.0, 0.0,
            2.0, 0.0,
            0.0, 2.0,
            0.0, 0.0))});
    assertTrue(CoverageCalculator.removeSmallParts(collection, AREA_THRESHOLD, geometryFactory) instanceof Polygon);
  }

  private LinearRing createRing(double ... values) {
    List<Coordinate> coords = new LinkedList<>();
    for (int i = 0; i < values.length; i += 2) {
      coords.add(new Coordinate(values[i], values[i+1]));
    }
    return geometryFactory.createLinearRing(coords.toArray(new Coordinate[0]));
  }
}
