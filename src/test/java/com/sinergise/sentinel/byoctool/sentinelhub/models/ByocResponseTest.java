package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocError.Error;
import org.junit.jupiter.api.Test;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.newObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByocResponseTest {

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

  @Test
  void deserializeErrorMessage() throws JsonProcessingException {
    ByocError byocError = newObjectMapper()
        .readValue("{ \"error\": { \"message\": \"error occured\" }}", ByocError.class);

    assertEquals("error occured", byocError.getError().getMessage());
  }

  @Test
  void deserializeListOfErrors() throws JsonProcessingException {
    ByocError byocError = newObjectMapper()
        .readValue("{ \"error\": { \"errors\": [ \"error occurred\" ] }}", ByocError.class);

    assertEquals("[\"error occurred\"]", byocError.getError().getErrors().toString());
  }

  @Test
  void deserializeMapOfErrors() throws JsonProcessingException {
    ByocError byocError = newObjectMapper()
        .readValue("{ \"error\": { \"errors\": { \"field\": \"error occurred\", \"field2\": \"another error\" }}}", ByocError.class);

    assertEquals("{\"field\":\"error occurred\",\"field2\":\"another error\"}", byocError.getError().getErrors().toString());
  }

  @Test
  void parseErrorMessage() {
    Error error = new Error();
    error.setMessage("error occurred");
    error.setErrors(newObjectMapper().createObjectNode().put("field", "bad value"));

    ByocError byocError = new ByocError();
    byocError.setError(error);

    String errorMessage = ServiceUtils.getErrorMessage(error, 404);

    assertEquals("error occurred (404): {\"field\":\"bad value\"}", errorMessage);
  }
}
