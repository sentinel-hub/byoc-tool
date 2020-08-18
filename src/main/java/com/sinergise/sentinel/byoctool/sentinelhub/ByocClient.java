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
import java.util.List;
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

  public ByocCollection getCollection(String collectionId) {
    Response response = collectionsTarget().path(collectionId).request().get();

    if (response.getStatus() == 200) {
      return response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
      }).getData();
    } else if (response.getStatus() == 404) {
      return null;
    } else {
      ByocResponse<?> shResponse = response.readEntity(new GenericType<ByocResponse<?>>() {
      });
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }

  public ByocTile getTile(String collectionId, String tileId) {
    Response response = tileTarget(collectionId, tileId).request().get();

    ensureStatus(response, 200);

    return response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
    }).getData();
  }

  public Optional<ByocTile> searchTile(String collectionId, String path) {
    Response response =
        tilesTarget(collectionId)
            .queryParam("path", path)
            .request()
            .get();

    Optional<List<ByocTile>> tiles = readResponse(response, new GenericType<ByocPage<ByocTile>>() {
    });
    return tiles.flatMap(page -> page.isEmpty()
        ? Optional.empty() : Optional.of(page.get(0)));
  }

  public Iterator<ByocTile> getTileIterator(String collectionId) {
    URI firstPageUrl = collectionsTarget().path(collectionId).path("tiles").getUri();
    return new PagingIterator(firstPageUrl, httpClient);
  }

  public String createCollection(ByocCollection collection) {
    Response response =
        collectionsTarget()
            .request()
            .post(Entity.entity(collection, MediaType.APPLICATION_JSON_TYPE));

    ensureStatus(response, 201);

    ByocResponse<ByocCollection> entity = response.readEntity(new GenericType<ByocResponse<ByocCollection>>() {
    });

    return entity.getData().getId();
  }

  public String createTile(String collectionId, ByocTile tile) {
    Response response =
        tilesTarget(collectionId)
            .request()
            .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ensureStatus(response, 201);

    ByocTile returnedTile =
        response.readEntity(new GenericType<ByocResponse<ByocTile>>() {
        }).getData();

    return returnedTile.getId();
  }

  public void updateTile(String collectionId, ByocTile tile) {
    Response response =
        tileTarget(collectionId, tile.getId())
            .request()
            .put(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

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
