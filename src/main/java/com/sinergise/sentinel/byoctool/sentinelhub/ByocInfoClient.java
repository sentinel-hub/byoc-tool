package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import java.util.Optional;
import java.util.function.Supplier;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

public class ByocInfoClient {

  private final WebTarget infoTarget;

  public ByocInfoClient(AuthClient authClient) {
    this(authClient::accessToken);
  }

  public ByocInfoClient(Supplier<String> accessTokenSupplier) {
    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(ObjectMapperFactory.newObjectMapper());

    ClientConfig clientConfig =
        new ClientConfig()
            .register(jsonProvider)
            .register(new AddTokenRequestFilter(accessTokenSupplier))
            .register(new UserAgentRequestFilter());
    Client httpClient = ClientBuilder.newClient(clientConfig);

    this.infoTarget = httpClient.target("https://services.sentinel-hub.com/api/v1/byoc");
  }

  public Optional<ByocCollectionInfo> getCollectionInfo(String collectionId) {
    Response response = infoTarget.path("global").path(collectionId).request().get();

    if (response.getStatus() == 200) {
      ByocCollectionInfo info =
          response.readEntity(new GenericType<ByocResponse<ByocCollectionInfo>>() {}).getData();
      return Optional.ofNullable(info);
    } else if (response.getStatus() == 404) {
      return Optional.empty();
    } else {
      ByocResponse<?> shResponse = response.readEntity(new GenericType<ByocResponse<?>>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }
}
