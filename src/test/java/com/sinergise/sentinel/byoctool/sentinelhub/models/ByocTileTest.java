package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils;
import org.junit.jupiter.api.Test;

import java.time.Instant;

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

  @Test
  void parseSensingTimeAsISO() throws JsonProcessingException {
    ByocTile tile = objectMapper.readValue(
        "{ \"sensingTime\": \"2019-10-21T14:51:46Z\"}",
        ByocTile.class);

    assertEquals(Instant.parse("2019-10-21T14:51:46Z"), tile.getSensingTime());
  }

  @Test
  void parseSensingTimeWithMillis() throws JsonProcessingException {
    ByocTile tile = objectMapper.readValue(
        "{ \"sensingTime\": \"2019-10-21T14:51:46.123Z\"}",
        ByocTile.class);

    assertEquals(Instant.parse("2019-10-21T14:51:46.123Z"), tile.getSensingTime());
  }
}
