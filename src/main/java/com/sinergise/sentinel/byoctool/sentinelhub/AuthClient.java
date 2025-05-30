package com.sinergise.sentinel.byoctool.sentinelhub;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

import static com.sinergise.sentinel.byoctool.sentinelhub.ServiceUtils.*;

@Log4j2
public class AuthClient {

  private static final String AUTH_SERVICE_BASE_URL;

  private static final String DEFAULT_AUTH_SERVICE_BASE_URL =
      "https://services.sentinel-hub.com/oauth";

  static {
    AUTH_SERVICE_BASE_URL =
        Optional.ofNullable(System.getenv("AUTH_SERVICE_BASE_URL"))
            .orElse(DEFAULT_AUTH_SERVICE_BASE_URL);
  }

  private final Client httpClient;
  private final MultivaluedHashMap<String, String> formData;

  private String accessToken;
  private Instant expiresIn;

  public AuthClient() {
    this(System.getenv("SH_CLIENT_ID"), System.getenv("SH_CLIENT_SECRET"));
  }

  public AuthClient(String clientId, String clientSecret) {
    Objects.requireNonNull(clientId, "client id missing");
    Objects.requireNonNull(clientSecret, "client secret missing");

    ObjectMapper objectMapper =
        new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(objectMapper);

    ClientConfig clientConfig =
        new ClientConfig()
            .register(jsonProvider);

    httpClient = newHttpClient(clientConfig);

    formData = new MultivaluedHashMap<>();
    formData.add("grant_type", "client_credentials");
    formData.add("client_id", clientId);
    formData.add("client_secret", clientSecret);
  }

  public String getAccessToken() {
    if (accessToken == null || Instant.now().isAfter(expiresIn)) {
      TokenResponse tokenResponse = newAccessToken();

      accessToken = tokenResponse.getAccessToken();
      expiresIn = Instant.now().plus(tokenResponse.getExpiresIn() - 10, ChronoUnit.SECONDS);
    }

    return accessToken;
  }

  private TokenResponse newAccessToken() {
    Response response = executeWithRetry(
        "Fetching an access token",
        () ->
            httpClient
                .target(AUTH_SERVICE_BASE_URL)
                .path("token")
                .request(MediaType.APPLICATION_JSON_TYPE)
                .post(Entity.form(formData)));

    ensureStatus(response, 200);

    return response.readEntity(TokenResponse.class);
  }

  @Getter
  @Setter
  private static class TokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("expires_in")
    private Integer expiresIn;
  }
}
