package com.sinergise.sentinel.byoctool.sentinelhub;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.ingestion.IngestionException;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocErrorResponse;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocErrorResponse.ByocError;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Log4j2
public class ServiceUtils {

  private static final String REQUEST_ID = String.format("byoc-tool-%s-%s",
      ByocTool.VERSION, UUID.randomUUID());
  private static final TracingFilter LOGGING_FILTER = new TracingFilter(REQUEST_ID);
  private static final UserAgentFilter USER_AGENT_FILTER = new UserAgentFilter();

  public static ObjectMapper newObjectMapper() {
    return new ObjectMapper()
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .setSerializationInclusion(Include.NON_NULL)
        .registerModule(new Jdk8Module())
        .registerModule(new JavaTimeModule());
  }

  static JacksonJsonProvider newJsonProvider() {
    JacksonJsonProvider jsonProvider = new JacksonJaxbJsonProvider();
    jsonProvider.setMapper(newObjectMapper());

    return jsonProvider;
  }

  static Client newHttpClient(ClientConfig clientConfig) {
    clientConfig.register(USER_AGENT_FILTER)
        .register(LOGGING_FILTER);

    return ClientBuilder.newBuilder()
        .withConfig(clientConfig)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build();
  }

  static void ensureStatus(Response response, int status) {
    if (response.getStatus() != status) {
      throw new RuntimeException(getErrorMessage(response));
    }
  }

  private static String getErrorMessage(Response response) {
    ByocError error = response.readEntity(ByocErrorResponse.class).getError();
    return getErrorMessage(error, response.getStatus());
  }

  public static String getErrorMessage(ByocError error, int statusCode) {
    String errorMessage = String.format("%s (%d)", error.getMessage(), statusCode);

    if (error.getErrors() != null) {
      errorMessage = String.format("%s: %s", errorMessage, error.getErrors());
    }

    return errorMessage;
  }

  static Response executeWithRetry(String logMessage, Supplier<Response> request) {
    String logMessageBase = logMessage;
    Response response = null;
    boolean requestFailed;
    int attempt = 0;

    do {
      attempt += 1;
      if (attempt > 1) {
        try {
          TimeUnit.SECONDS.sleep((attempt - 1) * 10L);
        } catch (InterruptedException ignored) {
        }

        logMessage = logMessageBase + String.format(" (attempt %d)", attempt);
      }

      log.info(logMessage);

      try {
        response = request.get();
        requestFailed = response.getStatusInfo().getFamily() == Family.SERVER_ERROR;
        if (requestFailed) {
          log.error("API request got back 5xx error: {}.", getErrorMessage(response));
        }
      } catch (Exception e) {
        requestFailed = true;
        log.error("Exception occurred while making an API request: {}", e.getMessage());
      }
    } while (requestFailed && attempt < 5);

    if (requestFailed) {
      throw new IngestionException("Failed to execute API request!");
    }

    return response;
  }

  private static class UserAgentFilter implements ClientRequestFilter {

    private static final String USER_AGENT = "byoc-tool/v" + ByocTool.VERSION;

    @Override
    public void filter(ClientRequestContext requestContext) {
      requestContext.getHeaders().add(HttpHeaders.USER_AGENT, USER_AGENT);
    }
  }

  @Log4j2
  private static class TracingFilter implements ClientRequestFilter {

    private final String requestId;

    TracingFilter(String requestId) {
      this.requestId = requestId;
      log.info("Request id is {}", requestId);
    }

    @Override
    public void filter(ClientRequestContext requestContext) {
      requestContext.getHeaders()
          .add("SH-Request-Id", requestId);
    }
  }

  @RequiredArgsConstructor
  static class AuthRequestFilter implements ClientRequestFilter {

    private final Supplier<String> accessTokenSupplier;

    @Override
    public void filter(ClientRequestContext requestContext) {
      requestContext.getHeaders().add("Authorization", "Bearer " + accessTokenSupplier.get());
    }
  }
}
