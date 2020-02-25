package byoc.sentinelhub;

import byoc.ByocTool;
import byoc.sentinelhub.models.ByocCollection;
import byoc.sentinelhub.models.ByocTile;
import byoc.sentinelhub.models.Common.Response;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;
import software.amazon.awssdk.regions.Region;

public class ByocClient {

  private static final String BYOC_SERVICE_BASE_URL;

  private static final String DEFAULT_BYOC_SERVICE_BASE_URL =
      "https://services.sentinel-hub.com/byoc";

  private static final String USER_AGENT = "byoc-tool/v" + ByocTool.VERSION;

  static {
    BYOC_SERVICE_BASE_URL =
        Optional.ofNullable(System.getenv("BYOC_SERVICE_BASE_URL"))
            .orElse(DEFAULT_BYOC_SERVICE_BASE_URL);
  }

  private final WebTarget webTarget;
  private final Client httpClient;

  public ByocClient() {
    this(new AuthClient());
  }

  public ByocClient(AuthClient authClient) {
    ObjectMapper objectMapper = newObjectMapper();

    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(objectMapper);

    ClientConfig clientConfig = new ClientConfig();
    clientConfig.register(jsonProvider);

    clientConfig.register((ClientRequestFilter) requestContext ->
        requestContext.getHeaders().add("Authorization", "Bearer " + authClient.accessToken()));

    clientConfig.register((ClientRequestFilter) requestContext ->
        requestContext.getHeaders().add(HttpHeaders.USER_AGENT, USER_AGENT));

    this.httpClient = ClientBuilder.newClient(clientConfig);
    this.webTarget = httpClient.target(BYOC_SERVICE_BASE_URL);
  }

  private static ObjectMapper newObjectMapper() {
    ObjectMapper objectMapper;
    objectMapper = new ObjectMapper();
    objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    objectMapper.setSerializationInclusion(Include.NON_NULL);
    objectMapper.registerModule(new Jdk8Module());
    objectMapper.registerModule(new JavaTimeModule());

    return objectMapper;
  }

  Client getHttpClient() {
    return httpClient;
  }

  WebTarget getWebTarget() {
    return webTarget;
  }

  public ByocCollection getCollection(String collectionId) {
    javax.ws.rs.core.Response response = webTarget.path("collections").path(collectionId).request().get();

    if (response.getStatus() == 200) {
      return response.readEntity(new GenericType<Response<ByocCollection>>() {}).getData();
    } else if (response.getStatus() == 404) {
      return null;
    } else {
      Response shResponse = response.readEntity(new GenericType<Response>() {});
      throw new RuntimeException(shResponse.getError().getMessage());
    }
  }

  public Region getCollectionS3Region(String collectionId) {
    javax.ws.rs.core.Response response = webTarget.path("global").queryParam("ids", collectionId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    String location =
        (String)
            response
                .readEntity(new GenericType<Response<List<Map<String, Object>>>>() {})
                .getData()
                .get(0)
                .get("location");

    return toS3Region(location);
  }

  public ByocTile getTile(String collectionId, String tileId) {
    javax.ws.rs.core.Response response = tileTarget(collectionId, tileId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    return response.readEntity(new GenericType<Response<ByocTile>>() {}).getData();
  }

  public Iterator<ByocTile> getTileIterator(String collectionId) {
    return new PagingTileIterator(this, collectionId);
  }

  public Iterator<ByocTile> getTileIterator(String collectionId, String... tileIds) {
    return new TileIterator(this, collectionId, Arrays.asList(tileIds));
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
    javax.ws.rs.core.Response response = webTarget.path("collections").request()
        .post(Entity.entity(collection, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 201);

    Response<ByocCollection> entity = response.readEntity(new GenericType<Response<ByocCollection>>() {});
    return UUID.fromString(entity.getData().getId());
  }

  public void createTile(String collectionId, ByocTile tile) {
    javax.ws.rs.core.Response response =
        tilesTarget(collectionId)
            .request()
            .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 201);
  }

  public void updateTile(String collectionId, ByocTile tile) {
    javax.ws.rs.core.Response response =
        tileTarget(collectionId, tile.getId())
            .request()
            .put(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 204);
  }

  private WebTarget tileTarget(String collectionId, String id) {
    return tilesTarget(collectionId).path(id);
  }

  private WebTarget collectionTarget(String collectionId) {
    return webTarget.path("collections").path(collectionId);
  }

  private WebTarget tilesTarget(String collectionId) {
    return collectionTarget(collectionId).path("tiles");
  }

  private Region toS3Region(String location) {
    Region region;
    switch (location) {
      case "aws-eu-central-1":
      case "sgs-hq":
        region = Region.EU_CENTRAL_1;
        break;
      case "aws-us-west-2":
        region = Region.US_EAST_2;
        break;
      default:
        throw new RuntimeException("Unexpected location " + location);
    }

    return region;
  }
}
