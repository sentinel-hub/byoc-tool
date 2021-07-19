package com.sinergise.sentinel.byoctool.sentinelhub;

import com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.AuthRequestFilter;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocPage;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocTile;
import lombok.RequiredArgsConstructor;
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

public interface ByocClient {

  Optional<ByocCollection> getCollection(String collectionId);

  Optional<ByocTile> getTile(String collectionId, String tileId);

  Optional<ByocTile> searchTile(String collectionId, String path);

  Iterator<ByocTile> getTileIterator(String collectionId);

  ByocCollection createCollection(ByocCollection collection);

  ByocTile createTile(String collectionId, ByocTile tile);

  void updateTile(String collectionId, ByocTile tile);

  static ByocClient newByocClient(AuthClient authClient, ByocDeployment byocDeployment) {
    return new ByocClientImpl(authClient::getAccessToken, byocDeployment.getServiceUrl());
  }

  static ByocClient newByocClient(Supplier<String> accessTokenSupplier, String byocServiceUrl) {
    return new ByocClientImpl(accessTokenSupplier, byocServiceUrl);
  }
  static ByocClient newByocClient(Supplier<String> accessTokenSupplier, ByocDeployment byocDeployment) {
    return new ByocClientImpl(accessTokenSupplier, byocDeployment.getServiceUrl());
  }

  class ByocClientImpl implements ByocClient {

    private final Client httpClient;
    private final WebTarget byocTarget;

    private ByocClientImpl(Supplier<String> accessTokenSupplier, String byocServiceUrl) {
      ClientConfig clientConfig =
          new ClientConfig()
              .register(newJsonProvider())
              .register(new AuthRequestFilter(accessTokenSupplier));

      this.httpClient = newHttpClient(clientConfig);
      this.byocTarget = httpClient.target(byocServiceUrl);
    }

    @Override
    public Optional<ByocCollection> getCollection(String collectionId) {
      Response response = executeWithRetry(
          "Fetching collection",
          () -> collectionsTarget().path(collectionId).request().get());

      if (response.getStatus() == 404) {
        return Optional.empty();
      }

      ensureStatus(response, 200);

      ByocCollection collection = response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
      }).getData();

      return Optional.of(collection);
    }

    @Override
    public Optional<ByocTile> getTile(String collectionId, String tileId) {
      Response response = executeWithRetry(
          "Fetching tile " + tileId,
          () -> tileTarget(collectionId, tileId).request().get());

      if (response.getStatus() == 404) {
        return Optional.empty();
      }

      ensureStatus(response, 200);

      ByocTile tile = response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
      }).getData();

      return Optional.of(tile);
    }

    @Override
    public Optional<ByocTile> searchTile(String collectionId, String path) {
      Response response = executeWithRetry(
          "Searching for tile " + path,
          () ->
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

    @Override
    public Iterator<ByocTile> getTileIterator(String collectionId) {
      URI firstPageUrl = collectionsTarget().path(collectionId).path("tiles").getUri();
      return new PagingIterator(firstPageUrl, httpClient);
    }

    @Override
    public ByocCollection createCollection(ByocCollection collection) {
      Response response = executeWithRetry(
          String.format("Creating collection %s (%s)", collection.getName(), collection),
          () ->
              collectionsTarget()
                  .request()
                  .post(Entity.entity(collection, MediaType.APPLICATION_JSON_TYPE)));

      ensureStatus(response, 201);

      return response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
      }).getData();
    }

    @Override
    public ByocTile createTile(String collectionId, ByocTile tile) {
      Response response = executeWithRetry(
          String.format("Creating tile %s (%s)", tile.getPath(), tile),
          () ->
              tilesTarget(collectionId)
                  .request()
                  .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE)));

      ensureStatus(response, 201);

      return response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
      }).getData();
    }

    @Override
    public void updateTile(String collectionId, ByocTile tile) {
      Response response = executeWithRetry(
          String.format("Updating tile %s (%s)", tile.getPath(), tile),
          () ->
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
}
