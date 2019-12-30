package byoc.sentinelhub;

import byoc.ByocTool;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

class UserAgentFilter implements ClientRequestFilter {

  private static final String USER_AGENT = "byoc-tool/v" + ByocTool.VERSION;

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add(HttpHeaders.USER_AGENT, USER_AGENT);
  }
}
