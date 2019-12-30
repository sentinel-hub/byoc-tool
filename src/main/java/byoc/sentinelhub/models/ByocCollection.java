package byoc.sentinelhub.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.Data;

@Data
public class ByocCollection implements NoJsonAutoDetect {

  @JsonProperty("id")
  private String id;

  @JsonProperty("s3Bucket")
  private String s3Bucket;

  @JsonProperty("additionalData")
  private AdditionalData additionalData;

  @Data
  public class AdditionalData {

    @JsonProperty("bands")
    private Map<String, Object> bands;
  }

  public Set<String> bandNames() {
    return getAdditionalData().getBands().keySet();
  }
}
