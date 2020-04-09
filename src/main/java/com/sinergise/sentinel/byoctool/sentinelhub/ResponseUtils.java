package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.Common.Response;
import javax.ws.rs.core.GenericType;

class ResponseUtils {

  static void ensureStatus(javax.ws.rs.core.Response response, int status) {
    if (response.getStatus() != status) {
      Response shResponse = response.readEntity(new GenericType<Response>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }
}
