package com.sinergise.sentinel.byoctool.ingestion;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.RetryableException;
import software.amazon.awssdk.core.interceptor.Context.ModifyHttpResponse;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.ExecutionInterceptor;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Disabled because it requires AWS credentials,
 * but it can be run on demand if you have them.
 */
@Disabled
class S3RetryTest {

  @Test
  void customNumberOfRetries() {
    RetryingExecutionInterceptor interceptor = new RetryingExecutionInterceptor();
    S3Client s3 = newS3Client(interceptor, RetryPolicy.builder().numRetries(10).build());

    HeadBucketRequest request = testRequest();

    Assertions.assertThrows(RetryableException.class,
        () -> s3.headBucket(request));

    assertEquals(11, interceptor.attempts.intValue());
  }

  @Test
  void customBaseDelay() {
    RetryingExecutionInterceptor interceptor = new RetryingExecutionInterceptor();
    RetryPolicy retryPolicy = RetryPolicy.builder()
        .numRetries(1)
        .backoffStrategy(EqualJitterBackoffStrategy.builder()
            .baseDelay(Duration.ofSeconds(10))
            .maxBackoffTime(Duration.ofSeconds(10))
            .build())
        .build();

    S3Client s3 = newS3Client(interceptor, retryPolicy);

    HeadBucketRequest request = testRequest();

    Assertions.assertThrows(RetryableException.class,
        () -> s3.headBucket(request));

    long secondsElapsed = Duration.between(interceptor.times.get(0), interceptor.times.get(1)).getSeconds();
    assertTrue(secondsElapsed > 5 && secondsElapsed < 10);
  }

  private S3Client newS3Client(RetryingExecutionInterceptor interceptor, RetryPolicy retryPolicy) {
    ClientOverrideConfiguration configuration = ClientOverrideConfiguration.builder()
        .addExecutionInterceptor(interceptor)
        .retryPolicy(retryPolicy)
        .build();

    return S3Client.builder()
        .region(Region.EU_CENTRAL_1)
        .overrideConfiguration(configuration)
        .build();
  }

  static class RetryingExecutionInterceptor implements ExecutionInterceptor {

    final List<Instant> times = new LinkedList<>();
    final AtomicInteger attempts = new AtomicInteger();

    @Override
    public SdkHttpResponse modifyHttpResponse(ModifyHttpResponse context, ExecutionAttributes executionAttributes) {
      attempts.incrementAndGet();
      times.add(Instant.now());
      throw RetryableException.builder().build();
    }
  }

  private static HeadBucketRequest testRequest() {
    return HeadBucketRequest.builder()
        .bucket("some-bucket")
        .build();
  }
}
