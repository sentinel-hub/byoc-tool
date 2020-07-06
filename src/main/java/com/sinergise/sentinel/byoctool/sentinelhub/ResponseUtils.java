package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

class ResponseUtils {

  static void ensureStatus(Response response, int status) {
    if (response.getStatus() != status) {
      ByocResponse<?> shResponse = response.readEntity(new GenericType<ByocResponse<?>>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }
}
