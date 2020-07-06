package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ByocPage<T> extends ByocResponse<List<T>> implements NoJsonAutoDetect {

  @JsonProperty("links")
  private Links links;

  @Getter
  @Setter
  public static class Links {

    @JsonProperty("next")
    private URI next;
  }
}
