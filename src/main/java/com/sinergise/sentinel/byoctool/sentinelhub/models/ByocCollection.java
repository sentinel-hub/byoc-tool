package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.ToString;

import java.util.Map;
import java.util.Set;

@Data
public class ByocCollection implements NoJsonAutoDetect {

  @JsonProperty("id")
  private String id;

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("name")
  private String name;

  @JsonProperty("s3Bucket")
  private String s3Bucket;

  @JsonProperty("additionalData")
  private AdditionalData additionalData;

  @Data
  public static class AdditionalData {

    @JsonProperty("bands")
    private Map<String, Object> bands;
  }

  public Set<String> getBands() {
    return getAdditionalData().getBands().keySet();
  }
}
