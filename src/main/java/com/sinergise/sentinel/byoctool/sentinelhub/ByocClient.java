package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocPage;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import org.glassfish.jersey.client.ClientConfig;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.net.URI;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;

public class ByocClient {

  private final Client httpClient;
  private final WebTarget byocTarget;

  public ByocClient(AuthClient authClient, ByocDeployment byocDeployment) {
    this(authClient::getAccessToken, byocDeployment);
  }

  public ByocClient(Supplier<String> accessTokenSupplier, ByocDeployment byocDeployment) {
    ClientConfig clientConfig =
        new ClientConfig()
            .register(newJsonProvider())
            .register(new AuthRequestFilter(accessTokenSupplier))
            .register(new UserAgentRequestFilter());

    this.httpClient = newHttpClient(clientConfig);
    this.byocTarget = httpClient.target(byocDeployment.getServiceUrl());
  }

  public Optional<ByocCollection> getCollection(String collectionId) {
    Response response = executeWithRetry(() ->
        collectionsTarget().path(collectionId).request().get());

    if (response.getStatus() == 404) {
      return Optional.empty();
    }

    ensureStatus(response, 200);

    ByocCollection collection = response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
    }).getData();

    return Optional.of(collection);
  }

  public Optional<ByocTile> getTile(String collectionId, String tileId) {
    Response response = executeWithRetry(() ->
        tileTarget(collectionId, tileId).request().get());

    if (response.getStatus() == 404) {
      return Optional.empty();
    }

    ensureStatus(response, 200);

    ByocTile tile = response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
    }).getData();

    return Optional.of(tile);
  }

  public Optional<ByocTile> searchTile(String collectionId, String path) {
    Response response = executeWithRetry(() ->
        tilesTarget(collectionId)
            .queryParam("path", path)
            .request()
            .get());

    return getTilesPage(response)
        .map(ByocResponse::getData)
        .flatMap(tiles -> tiles.stream().findFirst());
  }

  static Optional<ByocPage<ByocTile>> getTilesPage(Response response) {
    if (response.getStatus() == 404) {
      return Optional.empty();
    }

    ensureStatus(response, 200);

    ByocPage<ByocTile> page = response.readEntity(new GenericType<ByocPage<ByocTile>>() {
    });

    return Optional.of(page);
  }

  public Iterator<ByocTile> getTileIterator(String collectionId) {
    URI firstPageUrl = collectionsTarget().path(collectionId).path("tiles").getUri();
    return new PagingIterator(firstPageUrl, httpClient);
  }

  public ByocCollection createCollection(ByocCollection collection) {
    Response response = executeWithRetry(() ->
        collectionsTarget()
            .request()
            .post(Entity.entity(collection, MediaType.APPLICATION_JSON_TYPE)));

    ensureStatus(response, 201);

    return response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
    }).getData();
  }

  public ByocTile createTile(String collectionId, ByocTile tile) {
    Response response = executeWithRetry(() ->
        tilesTarget(collectionId)
            .request()
            .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE)));

    ensureStatus(response, 201);

    return response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
    }).getData();
  }

  public void updateTile(String collectionId, ByocTile tile) {
    Response response = executeWithRetry(() ->
        tileTarget(collectionId, tile.getId())
            .request()
            .put(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE)));

    ensureStatus(response, 204);
  }

  private WebTarget collectionsTarget() {
    return byocTarget.path("collections");
  }

  private WebTarget collectionTarget(String collectionId) {
    return collectionsTarget().path(collectionId);
  }

  private WebTarget tilesTarget(String collectionId) {
    return collectionTarget(collectionId).path("tiles");
  }

  private WebTarget tileTarget(String collectionId, String id) {
    return tilesTarget(collectionId).path(id);
  }
}
