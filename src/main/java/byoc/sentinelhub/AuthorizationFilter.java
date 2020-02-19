package byoc.sentinelhub;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

class AuthorizationFilter implements ClientRequestFilter {

  private final AuthClient authClient;

  AuthorizationFilter(AuthClient authClient) {
    this.authClient = authClient;
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("Authorization", "Bearer " + authClient.accessToken());
  }
}
