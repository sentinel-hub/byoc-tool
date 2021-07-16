package com.sinergise.sentinel.byoctool.sentinelhub.models;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocErrorResponse.ByocError;
import org.junit.jupiter.api.Test;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.newObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ByocErrorResponseTest {

  @Test
  void deserializeErrorMessage() throws JsonProcessingException {
    ByocErrorResponse errorResponse = newObjectMapper()
        .readValue("{ \"error\": { \"message\": \"error occured\" }}", ByocErrorResponse.class);

    assertEquals("error occured", errorResponse.getError().getMessage());
  }

  @Test
  void deserializeListOfErrors() throws JsonProcessingException {
    ByocErrorResponse errorResponse = newObjectMapper()
        .readValue("{ \"error\": { \"errors\": [ \"error occurred\" ] }}", ByocErrorResponse.class);

    assertEquals("[\"error occurred\"]", errorResponse.getError().getErrors().toString());
  }

  @Test
  void deserializeMapOfErrors() throws JsonProcessingException {
    ByocErrorResponse errorResponse = newObjectMapper()
        .readValue("{ \"error\": { \"errors\": { \"field\": \"error occurred\", \"field2\": \"another error\" }}}", ByocErrorResponse.class);

    assertEquals("{\"field\":\"error occurred\",\"field2\":\"another error\"}", errorResponse.getError().getErrors().toString());
  }

  @Test
  void includeErrorsInErrorMessage() {
    ByocError error = new ByocError();
    error.setMessage("error occurred");
    error.setErrors(newObjectMapper().createObjectNode().put("field", "bad value"));

    ByocErrorResponse errorResponse = new ByocErrorResponse();
    errorResponse.setError(error);

    String errorMessage = ServiceUtils.getErrorMessage(error, 404);

    assertEquals("error occurred (404): {\"field\":\"bad value\"}", errorMessage);
  }
 }
