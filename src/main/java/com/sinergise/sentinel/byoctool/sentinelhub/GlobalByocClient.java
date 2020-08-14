package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import org.glassfish.jersey.client.ClientConfig;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import java.util.Optional;
import java.util.function.Supplier;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;

public class GlobalByocClient {

  private static final String RESOURCE_URL = "https://services.sentinel-hub.com/api/v1/byoc/global";

  private final WebTarget resourceTarget;

  public GlobalByocClient(AuthClient authClient) {
    this(authClient::accessToken);
  }

  public GlobalByocClient(Supplier<String> accessTokenSupplier) {
    ClientConfig clientConfig =
        new ClientConfig()
            .register(newJsonProvider())
            .register(new AuthRequestFilter(accessTokenSupplier))
            .register(new UserAgentRequestFilter());

    Client httpClient = newHttpClient(clientConfig);
    this.resourceTarget = httpClient.target(RESOURCE_URL);
  }

  public Optional<ByocCollectionInfo> getCollectionInfo(String collectionId) {
    Response response = resourceTarget.path(collectionId).request().get();
    return readResponse(response, new GenericType<ByocResponse<ByocCollectionInfo>>() {
    });
  }
}
