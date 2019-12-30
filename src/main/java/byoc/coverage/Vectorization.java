package byoc.coverage;

import byoc.tiff.TiffCompoundDirectory;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.function.Predicate;
import lombok.Value;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.operation.union.CascadedPolygonUnion;

class Vectorization {

  static Geometry vectorize(BufferedImage image, TiffCompoundDirectory directory) {
    Envelope envelope = directory.envelope();

    AffineTransform affineTransform =
        new AffineTransform(
            envelope.getWidth() / image.getWidth(),
            -envelope.getHeight() / image.getHeight(),
            envelope.getMinX(),
            envelope.getMaxY());

    Integer noDataValue = directory.noDataValueAsInt();
    Predicate<Number> pixelPredicate = val -> !val.equals(noDataValue);

    return vectorize(image, affineTransform, pixelPredicate);
  }

  private static Geometry vectorize(
      BufferedImage img, AffineTransform affine, Predicate<Number> predicate) {
    return vectorize(new ForkJoinPool(1), img, affine, predicate);
  }

  private static Geometry vectorize(
      ForkJoinPool pool, BufferedImage img, AffineTransform affine, Predicate<Number> predicate) {

    Raster raster = img.getRaster();
    GeometryFactory geometryFactory = new GeometryFactory();

    List<Geometry> geoms = new ArrayList<>();

    double pxSizeX = affine.getScaleX();
    double pxSizeY = affine.getScaleY();

    int heightPixels = img.getHeight();
    int widthPixels = img.getWidth();

    int transferType = raster.getTransferType();
    boolean isFloatDoubleType = isFloatDoubleType(transferType);

    for (int y = 0; y < heightPixels; y++) {

      List<Geometry> innerPolys = new ArrayList<>();
      Coordinate prevPoint = null;

      for (int x = 0; x < widthPixels; x++) {

        Number value = getValue(raster, x, y, isFloatDoubleType);

        if (predicate.test(value)) {
          if (prevPoint == null) {
            prevPoint = new Coordinate(x, y);
          }
          if (x == widthPixels - 1) {
            double x1 = affine.getTranslateX() + prevPoint.x * pxSizeX;
            double y1 = affine.getTranslateY() + prevPoint.y * pxSizeY;
            double x2 = affine.getTranslateX() + (x + 1) * pxSizeX;
            double y2 = affine.getTranslateY() + (y + 1) * pxSizeY;
            Envelope pixelEnv = new Envelope(x1, x2, y1, y2);
            innerPolys.add(geometryFactory.toGeometry(pixelEnv));
            prevPoint = null;
          }
        } else {
          if (prevPoint == null) {
            continue;
          }

          double x1 = affine.getTranslateX() + prevPoint.x * pxSizeX;
          double y1 = affine.getTranslateY() + prevPoint.y * pxSizeY;
          double x2 = affine.getTranslateX() + x * pxSizeX;
          double y2 = affine.getTranslateY() + (y + 1) * pxSizeY;
          Envelope pixelEnv = new Envelope(x1, x2, y1, y2);
          innerPolys.add(geometryFactory.toGeometry(pixelEnv));
          prevPoint = null;
        }
      }

      if (innerPolys.isEmpty()) {
        continue;
      }

      Geometry innerUnion = pool.invoke(new GeometryUnionOp(innerPolys));
      geoms.add(innerUnion);
    }

    return pool.invoke(new GeometryUnionOp(geoms));
  }

  private static boolean isFloatDoubleType(int transferType) {
    return transferType == DataBuffer.TYPE_FLOAT || transferType == DataBuffer.TYPE_DOUBLE;
  }

  private static Number getValue(Raster raster, int x, int y, boolean isFloatDoubleType) {
    if (isFloatDoubleType) {
      return raster.getSampleDouble(x, y, 0);
    }
    return raster.getSample(x, y, 0);
  }

  private static class GeometryUnionOp extends RecursiveTask<Geometry> {

    private List<Geometry> geomsToUnion;

    GeometryUnionOp(List<Geometry> list) {
      this.geomsToUnion = list;
    }

    @Override
    protected Geometry compute() {
      if (geomsToUnion.size() <= 4) {
        return new CascadedPolygonUnion(geomsToUnion).union();
      }

      int size = geomsToUnion.size();
      GeometryUnionOp subUnion1 = new GeometryUnionOp(geomsToUnion.subList(0, size / 2));
      GeometryUnionOp subUnion2 = new GeometryUnionOp(geomsToUnion.subList(size / 2, size));

      subUnion1.fork();
      subUnion2.fork();

      return new GeometryUnionOp(Arrays.asList(subUnion1.join(), subUnion2.join())).invoke();
    }
  }

  @Value
  private static final class AffineTransform {

    private final double scaleX;
    private final double scaleY;
    private final double translateX;
    private final double translateY;
  }
}
