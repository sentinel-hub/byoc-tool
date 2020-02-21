package byoc.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ByocCollection implements Common.NoJsonAutoDetect {

  @JsonProperty("id")
  private String id;

  @JsonProperty("name")
  private String name;

  @JsonProperty("s3Bucket")
  private String s3Bucket;

  @JsonProperty("additionalData")
  private AdditionalData additionalData;

  @Getter
  @Setter
  public class AdditionalData {

    @JsonProperty("bands")
    private Map<String, Object> bands;
  }

  public Set<String> getBands() {
    return getAdditionalData().getBands().keySet();
  }
}
