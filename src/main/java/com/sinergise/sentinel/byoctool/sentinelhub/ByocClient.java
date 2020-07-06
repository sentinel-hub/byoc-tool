package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

public class ByocClient {

  private final Client httpClient;
  private final WebTarget byocTarget;

  public ByocClient(AuthClient authClient, String locationId) {
    this(authClient::accessToken, locationId);
  }

  public ByocClient(Supplier<String> accessTokenSupplier, String locationId) {
    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(ObjectMapperFactory.newObjectMapper());

    ClientConfig clientConfig =
        new ClientConfig()
            .register(jsonProvider)
            .register(new AddTokenRequestFilter(accessTokenSupplier))
            .register(new UserAgentRequestFilter());
    Client httpClient = ClientBuilder.newClient(clientConfig);

    this.httpClient = ClientBuilder.newClient(clientConfig);
    this.byocTarget = httpClient.target(getServiceUrl(locationId));
  }

  private String getServiceUrl(String locationId) {
    final String serviceUrl;

    switch (locationId) {
      case "aws-eu-central-1":
        serviceUrl = "https://services.sentinel-hub.com/api/v1/byoc";
        break;
      case "aws-us-west-2":
        serviceUrl = "https://services-uswest2.sentinel-hub.com/api/v1/byoc";
        break;
      default:
        throw new RuntimeException("Unexpected location " + locationId);
    }

    return serviceUrl;
  }

  Client getHttpClient() {
    return httpClient;
  }

  WebTarget getByocTarget() {
    return byocTarget;
  }

  public ByocCollection getCollection(String collectionId) {
    Response response = byocTarget.path("collections").path(collectionId).request().get();

    if (response.getStatus() == 200) {
      return response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {}).getData();
    } else if (response.getStatus() == 404) {
      return null;
    } else {
      ByocResponse<?> shResponse = response.readEntity(new GenericType<ByocResponse<?>>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }

  public ByocTile getTile(String collectionId, String tileId) {
    Response response = tileTarget(collectionId, tileId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    return response.readEntity(new GenericType<ByocResponse<ByocTile>>() {}).getData();
  }

  public Iterator<ByocTile> getTileIterator(String collectionId) {
    return new PagingTileIterator(this, collectionId);
  }

  public Set<String> getTilePaths(String collectionId) {
    Set<String> tilePaths = new HashSet<>();

    Iterator<ByocTile> iter = getTileIterator(collectionId);
    while (iter.hasNext()) {
      tilePaths.add(iter.next().getPath());
    }

    return tilePaths;
  }

  public UUID createCollection(ByocCollection collection) {
    Response response =
        byocTarget
            .path("collections")
            .request()
            .post(Entity.entity(collection, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 201);

    ByocResponse<ByocCollection> entity =
        response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {});
    return UUID.fromString(entity.getData().getId());
  }

  public String createTile(String collectionId, ByocTile tile) {
    Response response =
        tilesTarget(collectionId)
            .request()
            .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 201);

    ByocTile returnedTile =
        response.readEntity(new GenericType<ByocResponse<ByocTile>>() {}).getData();

    return returnedTile.getId();
  }

  public void updateTile(String collectionId, ByocTile tile) {
    Response response =
        tileTarget(collectionId, tile.getId())
            .request()
            .put(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 204);
  }

  private WebTarget tileTarget(String collectionId, String id) {
    return tilesTarget(collectionId).path(id);
  }

  private WebTarget collectionTarget(String collectionId) {
    return byocTarget.path("collections").path(collectionId);
  }

  private WebTarget tilesTarget(String collectionId) {
    return collectionTarget(collectionId).path("tiles");
  }
}
