package byoc.sentinelhub.models;

import byoc.sentinelhub.Constants;
import byoc.utils.WktToGeoJson;
import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import org.geojson.GeoJsonObject;
import org.locationtech.jts.geom.Geometry;

@Data
public class ByocTile implements NoJsonAutoDetect {

  @JsonProperty("id")
  private String id;

  @JsonProperty("path")
  private String path;

  @JsonProperty("status")
  private String status;

  @JsonProperty("sensingTime")
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
  private LocalDateTime sensingTime;

  @JsonProperty("coverGeometry")
  private GeoJsonObject coverGeometry;

  private Map<String, Object> other = new HashMap<>();

  @JsonAnySetter
  public void set(String name, Object value) {
    other.put(name, value);
  }

  @JsonAnyGetter
  public Map<String, Object> any() {
    return other;
  }

  public String idWithPath() {
    return String.format("%s\t%s", getId(), getPath());
  }

  public String bandPath(String band) {
    return getPath().replace(Constants.BAND_PLACEHOLDER, band);
  }

  public void setCoverGeometry(Geometry coverage) {
    setCoverGeometry(WktToGeoJson.convert(coverage));
  }

  private void setCoverGeometry(GeoJsonObject coverage) {
    this.coverGeometry = coverage;
  }
}
