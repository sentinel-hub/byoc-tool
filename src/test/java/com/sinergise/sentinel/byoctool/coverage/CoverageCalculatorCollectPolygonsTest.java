package com.sinergise.sentinel.byoctool.coverage;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.*;

import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CoverageCalculatorCollectPolygonsTest {

  private GeometryFactory geometryFactory = new GeometryFactory();

  @Test
  void pointRemoved() {
    Geometry point = geometryFactory.createPoint(new Coordinate(0.0, 0.0));
    assertTrue(CoverageCalculator.collectPolygons(point, geometryFactory).isEmpty());
  }

  @Test
  void lineStringRemoved() {
    Geometry lineString = createRing(
        0.0, 0.0,
        1.0, 0.0,
        0.0, 1.0,
        0.0, 0.0);
    assertTrue(CoverageCalculator.collectPolygons(lineString, geometryFactory).isEmpty());
  }

  @Test
  void polygonRetained() {
    Geometry poly = geometryFactory.createPolygon(
        createRing(0.0, 0.0,
            1.0, 0.0,
            0.0, 1.0,
            0.0, 0.0));
    assertFalse(CoverageCalculator.collectPolygons(poly, geometryFactory).isEmpty());
  }

  @Test
  void pointRemovedStructureFlattened() {
    Geometry point = geometryFactory.createPoint(new Coordinate(0.0, 0.0));
    Polygon poly = geometryFactory.createPolygon(
            createRing(0.0, 0.0,
                    1.0, 0.0,
                    0.0, 1.0,
                    0.0, 0.0));
    Geometry multiPoly = geometryFactory.createMultiPolygon(new Polygon[]{poly, poly});
    Geometry all = geometryFactory.createGeometryCollection(new Geometry[]{point, multiPoly});
    Geometry collectedPolys = CoverageCalculator.collectPolygons(all, geometryFactory);
    assertTrue(collectedPolys instanceof MultiPolygon);
    assertEquals(2, collectedPolys.getNumGeometries());
  }

  private LinearRing createRing(double ... values) {
    List<Coordinate> coords = new LinkedList<>();
    for (int i = 0; i < values.length; i += 2) {
      coords.add(new Coordinate(values[i], values[i+1]));
    }
    return geometryFactory.createLinearRing(coords.toArray(new Coordinate[0]));
  }
}
