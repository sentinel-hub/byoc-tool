package byoc.sentinelhub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import byoc.sentinelhub.models.ByocTile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ByocServiceTest {

  static ObjectMapper objectMapper() {
    return ByocClient.newObjectMapper();
  }

  @Test
  void deserializeUnknownAttribute() throws JsonProcessingException {
    ByocTile tile = objectMapper().readValue("{ \"unknown_attribute\": true }", ByocTile.class);

    assertTrue(tile.getOther().containsKey("unknown_attribute"));
  }

  @Test
  void serializeUnknownAttribute() {
    ByocTile tile = new ByocTile();
    tile.set("unknown_attribute", true);

    assertTrue(objectMapper().valueToTree(tile).has("unknown_attribute"));
  }
}
