package byoc.sentinelhub;

import byoc.sentinelhub.models.ByocCollection;
import byoc.sentinelhub.models.ByocTile;
import byoc.sentinelhub.models.SHResponse;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.*;
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
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

public class ByocClient {

  private static final String BYOC_SERVICE_BASE_URL;

  private static final String DEFAULT_BYOC_SERVICE_BASE_URL =
      "https://services.sentinel-hub.com/byoc";

  static {
    BYOC_SERVICE_BASE_URL =
        Optional.ofNullable(System.getenv("BYOC_SERVICE_BASE_URL"))
            .orElse(DEFAULT_BYOC_SERVICE_BASE_URL);
  }

  private final WebTarget webTarget;
  private final Client httpClient;
  private final S3ClientBuilder s3ClientBuilder;

  public ByocClient(AuthClient authClient, S3ClientBuilder s3ClientBuilder) {
    ObjectMapper objectMapper = newObjectMapper();

    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(objectMapper);

    ClientConfig clientConfig = new ClientConfig();
    clientConfig.register(jsonProvider);
    clientConfig.register(new AuthorizationFilter(authClient));
    clientConfig.register(new UserAgentFilter());

    this.httpClient = ClientBuilder.newClient(clientConfig);
    this.webTarget = httpClient.target(BYOC_SERVICE_BASE_URL);
    this.s3ClientBuilder = s3ClientBuilder;
  }

  static ObjectMapper newObjectMapper() {
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
    Response response = webTarget.path("collections").path(collectionId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    return response.readEntity(new GenericType<SHResponse<ByocCollection>>() {}).getData();
  }

  public Region getCollectionS3Region(String collectionId) {
    Response response = webTarget.path("global").queryParam("ids", collectionId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    String location =
        (String)
            response
                .readEntity(new GenericType<SHResponse<List<Map<String, Object>>>>() {})
                .getData()
                .get(0)
                .get("location");

    return getS3Region(location);
  }

  public S3Client getS3ClientForCollection(String collectionId) {
    return s3ClientBuilder.region(getCollectionS3Region(collectionId)).build();
  }

  public ByocTile getTile(String collectionId, String tileId) {
    Response response = tileTarget(collectionId, tileId).request().get();

    ResponseUtils.ensureStatus(response, 200);

    return response.readEntity(new GenericType<SHResponse<ByocTile>>() {}).getData();
  }

  public Iterator<ByocTile> getTileIterator(String collectionId) {
    return new PagingTileIterator(this, collectionId);
  }

  public Iterator<ByocTile> getTileIterator(String collectionId, String... tileIds) {
    return new TileIterator(this, collectionId, Arrays.asList(tileIds));
  }

  public Set<String> getAllTilePaths(String collectionId) {
    Set<String> tilePaths = new HashSet<>();

    Iterator<ByocTile> iter = getTileIterator(collectionId);
    while (iter.hasNext()) {
      tilePaths.add(iter.next().getPath());
    }

    return tilePaths;
  }

  public void createTile(String collectionId, ByocTile tile) {
    Response response =
        tilesTarget(collectionId)
            .request()
            .post(Entity.entity(tile, MediaType.APPLICATION_JSON_TYPE));

    ResponseUtils.ensureStatus(response, 201);
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
    return webTarget.path("collections").path(collectionId);
  }

  private WebTarget tilesTarget(String collectionId) {
    return collectionTarget(collectionId).path("tiles");
  }

  private Region getS3Region(String location) {
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