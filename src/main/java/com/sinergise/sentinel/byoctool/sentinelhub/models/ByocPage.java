package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.net.URI;
import java.util.List;

@Data
public class ByocPage<T> extends ByocResponse<List<T>> implements NoJsonAutoDetect {

  @JsonProperty("links")
  private Links links;

  @Data
  public static class Links {

    @JsonProperty("next")
    private URI next;
  }
}
