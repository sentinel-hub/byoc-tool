package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.sinergise.sentinel.byoctool.sentinelhub.Constants;
import com.sinergise.sentinel.byoctool.utils.JtsUtils;
import lombok.Getter;
import lombok.Setter;
import org.geojson.GeoJsonObject;
import org.locationtech.jts.geom.Geometry;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
public class ByocTile implements Common.NoJsonAutoDetect {

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

  @JsonProperty("additionalData")
  private AdditionalData additionalData;

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

  public void setJtsCoverGeometry(Geometry coverage) {
    setCoverGeometry(JtsUtils.toGeoJson(coverage));
  }

  @Getter
  @Setter
  public static class AdditionalData {

    @JsonProperty("failedIngestionCause")
    private String failedIngestionCause;
  }
}
