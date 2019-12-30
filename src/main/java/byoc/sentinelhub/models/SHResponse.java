package byoc.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SHResponse<T> implements NoJsonAutoDetect {

  @JsonProperty("data")
  private T data;

  @JsonProperty("error")
  private Error error;

  @Data
  public static class Error {

    @JsonProperty("message")
    private String message;
  }
}
