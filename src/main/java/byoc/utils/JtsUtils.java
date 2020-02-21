package byoc.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.geojson.Crs;
import org.geojson.GeoJsonObject;
import org.locationtech.jts.geom.Geometry;
import org.wololo.jts2geojson.GeoJSONWriter;

public class JtsUtils {

  private static final GeoJSONWriter GEO_JSON_WRITER = new GeoJSONWriter();
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static GeoJsonObject toGeoJson(Geometry geometry) {
    try {
      String geoJsonString = GEO_JSON_WRITER.write(geometry).toString();
      GeoJsonObject geoJson = OBJECT_MAPPER.readValue(geoJsonString, GeoJsonObject.class);

      Crs crs = new Crs();
      crs.getProperties().put("name", "urn:ogc:def:crs:EPSG::" + geometry.getSRID());
      geoJson.setCrs(crs);

      return geoJson;
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
