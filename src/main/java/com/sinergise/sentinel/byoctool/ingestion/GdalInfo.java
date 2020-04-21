package com.sinergise.sentinel.byoctool.ingestion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class GdalInfo {

  @JsonProperty("bands")
  private List<Band> bands;

  @Getter
  @Setter
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Band {

    @JsonProperty("band")
    private Integer band;

    @JsonProperty("type")
    private String type;
  }
}
