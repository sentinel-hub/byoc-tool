package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ByocResponse<T> implements NoJsonAutoDetect {

  @JsonProperty("data")
  private T data;
}
