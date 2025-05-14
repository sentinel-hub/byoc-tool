package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import org.glassfish.jersey.client.ClientConfig;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.Response;
import java.util.Optional;
import java.util.function.Supplier;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;

public class GlobalByocClient {

  private static final String RESOURCE_URL = "https://services.sentinel-hub.com/api/v1/byoc/global";

  private final WebTarget resourceTarget;

  public GlobalByocClient(AuthClient authClient) {
    this(authClient::getAccessToken);
  }

  public GlobalByocClient(Supplier<String> accessTokenSupplier) {
    ClientConfig clientConfig =
        new ClientConfig()
            .register(newJsonProvider())
            .register(new AuthRequestFilter(accessTokenSupplier));

    Client httpClient = newHttpClient(clientConfig);
    this.resourceTarget = httpClient.target(RESOURCE_URL);
  }

  public Optional<ByocCollectionInfo> getCollectionInfo(String collectionId) {
    Response response = executeWithRetry(
        "Fetching location of the collection",
        () ->
            resourceTarget.path(collectionId).request().get());

    if (response.getStatus() == 404) {
      return Optional.empty();
    }

    ensureStatus(response, 200);

    ByocCollectionInfo info = response.readEntity(new GenericType<ByocResponse<ByocCollectionInfo>>() {
    }).getData();

    return Optional.of(info);
  }
}
