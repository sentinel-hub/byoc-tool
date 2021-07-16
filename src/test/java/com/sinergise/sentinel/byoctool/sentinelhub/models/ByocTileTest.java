package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils;
import org.junit.jupiter.api.Test;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.newObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByocTileTest {

  private final ObjectMapper objectMapper = ServiceUtils.newObjectMapper();

  @Test
  void deserializeUnknownAttribute() throws JsonProcessingException {
    ByocTile tile = objectMapper
        .readValue("{ \"unknown_attribute\": true }", ByocTile.class);

    assertTrue(tile.getOther().containsKey("unknown_attribute"));
  }

  @Test
  void serializeUnknownAttribute() {
    ByocTile tile = new ByocTile();
    tile.set("unknown_attribute", true);

    JsonNode jsonNode = objectMapper.valueToTree(tile);

    assertTrue(jsonNode.has("unknown_attribute"));
  }
}
