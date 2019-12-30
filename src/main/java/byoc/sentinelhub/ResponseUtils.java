package byoc.sentinelhub;

import byoc.sentinelhub.models.SHResponse;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;

class ResponseUtils {

  static void ensureStatus(Response response, int status) {
    if (response.getStatus() != status) {
      SHResponse shResponse = response.readEntity(new GenericType<SHResponse>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }
}
