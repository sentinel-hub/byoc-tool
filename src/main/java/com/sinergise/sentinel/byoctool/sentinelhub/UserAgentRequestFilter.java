package com.sinergise.sentinel.byoctool.sentinelhub;

import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;

class UserAgentRequestFilter implements ClientRequestFilter {

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add(HttpHeaders.USER_AGENT, Constants.USER_AGENT);
  }
}
