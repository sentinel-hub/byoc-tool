package com.sinergise.sentinel.byoctool.sentinelhub;

import java.util.function.Supplier;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;

class AddTokenRequestFilter implements ClientRequestFilter {

  private final Supplier<String> accessTokenSupplier;

  AddTokenRequestFilter(Supplier<String> accessTokenSupplier) {
    this.accessTokenSupplier = accessTokenSupplier;
  }

  @Override
  public void filter(ClientRequestContext requestContext) {
    requestContext.getHeaders().add("Authorization", "Bearer " + accessTokenSupplier.get());
  }
}
