package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ByocError {

  @JsonProperty("error")
  private Error error;

  @Data
  public static class Error {

    @JsonProperty("message")
    private String message;

    @JsonProperty("errors")
    private JsonNode errors;
  }
}
