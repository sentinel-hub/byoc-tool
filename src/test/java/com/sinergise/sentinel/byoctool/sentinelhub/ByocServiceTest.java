package com.sinergise.sentinel.byoctool.sentinelhub;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import org.junit.jupiter.api.Test;

class ByocServiceTest {

  static ObjectMapper objectMapper() {
    return ServiceUtils.newObjectMapper();
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
