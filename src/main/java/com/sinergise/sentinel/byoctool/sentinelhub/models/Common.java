package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

public class Common {

  @Getter
  @Setter
  public static class Page<T> extends Response<List<T>> implements NoJsonAutoDetect {

    @JsonProperty("links")
    private Links links;

    @Getter
    @Setter
    public static class Links {

      @JsonProperty("next")
      private URI next;
    }
  }

  @Getter
  @Setter
  public static class Response<T> implements NoJsonAutoDetect {

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

  @JsonAutoDetect(
      isGetterVisibility = Visibility.NONE,
      getterVisibility = Visibility.NONE,
      setterVisibility = Visibility.NONE,
      creatorVisibility = Visibility.NONE)
  interface NoJsonAutoDetect {}
}
