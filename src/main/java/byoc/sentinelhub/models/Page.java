package byoc.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class Page<T> extends SHResponse<List<T>> implements NoJsonAutoDetect {

  @JsonProperty("links")
  private Links links;

  @Data
  public static class Links {

    @JsonProperty("next")
    private URI next;
  }
}
