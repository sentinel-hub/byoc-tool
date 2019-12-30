package byoc.sentinelhub;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

class AuthorizationFilter implements ClientRequestFilter {

  private final AuthService authService;

  AuthorizationFilter(AuthService authService) {
    this.authService = authService;
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("Authorization", "Bearer " + authService.accessToken());
  }
}
