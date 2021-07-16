package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.newObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByocTileTest {

  @Test
  void deserializeUnknownAttribute() throws JsonProcessingException {
    ByocTile tile = newObjectMapper()
        .readValue("{ \"unknown_attribute\": true }", ByocTile.class);

    assertTrue(tile.getOther().containsKey("unknown_attribute"));
  }

  @Test
  void serializeUnknownAttribute() {
    ByocTile tile = new ByocTile();
    tile.set("unknown_attribute", true);

    JsonNode jsonNode = newObjectMapper().valueToTree(tile);

    assertTrue(jsonNode.has("unknown_attribute"));
  }
}
