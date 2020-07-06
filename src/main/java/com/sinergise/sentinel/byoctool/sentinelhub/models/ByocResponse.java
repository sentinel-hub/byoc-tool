package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ByocResponse<T> implements NoJsonAutoDetect {

  @JsonProperty("data")
  private T data;

  @JsonProperty("error")
  private Error error;

  @Getter
  @Setter
  public static class Error {

    @JsonProperty("message")
    private String message;
  }
}
