package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.geojson.GeoJsonObject;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Data
public class ByocTile implements NoJsonAutoDetect {

  public static final String BAND_PLACEHOLDER = "(BAND)";

  @JsonProperty("id")
  private String id;

  @JsonProperty("path")
  private String path;

  @JsonProperty("status")
  private String status;

  @JsonProperty("sensingTime")
  private Instant sensingTime;

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

  @Getter
  @Setter
  public static class AdditionalData {

    @JsonProperty("failedIngestionCause")
    private String failedIngestionCause;
  }
}
