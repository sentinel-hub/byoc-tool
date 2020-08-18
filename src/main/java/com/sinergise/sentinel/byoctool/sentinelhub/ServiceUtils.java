package com.sinergise.sentinel.byoctool.sentinelhub;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sinergise.sentinel.byoctool.ByocTool;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocResponse;
import lombok.RequiredArgsConstructor;
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
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

class ServiceUtils {

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

  static <U,V extends ByocResponse<U>> Optional<U> readResponse(Response response, GenericType<V> entityType) {
    if (response.getStatus() == 200) {
      U data = response.readEntity(entityType).getData();
      return Optional.ofNullable(data);
    } else if (response.getStatus() == 404) {
      return Optional.empty();
    } else {
      throw new RuntimeException(parseErrorMessage(response));
    }
  }

  static class UserAgentRequestFilter implements ClientRequestFilter {

    private static final String USER_AGENT = "byoc-tool/v" + ByocTool.VERSION;

    @Override
    public void filter(ClientRequestContext requestContext) {
      requestContext.getHeaders().add(HttpHeaders.USER_AGENT, USER_AGENT);
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
