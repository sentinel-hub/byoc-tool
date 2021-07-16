package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ByocErrorResponse {

  @JsonProperty("error")
  private ByocError error;

  @Data
  public static class ByocError {

    @JsonProperty("message")
    private String message;

    @JsonProperty("errors")
    private JsonNode errors;
  }
}
