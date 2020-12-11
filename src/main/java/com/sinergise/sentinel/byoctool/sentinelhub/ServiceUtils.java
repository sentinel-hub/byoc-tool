package com.sinergise.sentinel.byoctool.sentinelhub;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import org.glassfish.jersey.jackson.internal.jackson.jaxrs.json.JacksonJsonProvider;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Log4j2
class ServiceUtils {

  private static final String REQUEST_ID = String.format("byoc-tool-%s-%s",
      ByocTool.VERSION, UUID.randomUUID());
  private static final TracingFilter LOGGING_FILTER = new TracingFilter(REQUEST_ID);
  private static final UserAgentFilter USER_AGENT_FILTER = new UserAgentFilter();

  static ObjectMapper newObjectMapper() {
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
      throw new RuntimeException(parseErrorMessage(response));
    }
  }

  private static String parseErrorMessage(Response response) {
    ByocResponse<?> shResponse = response.readEntity(new GenericType<ByocResponse<?>>() {
    });
    return shResponse.getError().getMessage();
  }

  static Response executeWithRetry(String logMessage, Supplier<Response> request) {
    String logMessageCopy = logMessage;
    Response response;
    boolean requestFailed;
    int attempt = 0;
    do {
      attempt += 1;
      if (attempt > 1) {
        try {
          TimeUnit.SECONDS.sleep(10);
        } catch (InterruptedException ignored) {
        }

        logMessage = logMessageCopy + String.format(" (attempt %d)", attempt);
      }

      log.info(logMessage);
      response = request.get();
      requestFailed = response.getStatusInfo().getFamily() == Family.SERVER_ERROR;
    } while (requestFailed && attempt < 5);

    if (requestFailed) {
      throw new RuntimeException(parseErrorMessage(response));
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
