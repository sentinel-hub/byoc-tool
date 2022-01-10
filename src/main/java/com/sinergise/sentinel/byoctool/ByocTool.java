package com.sinergise.sentinel.byoctool;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.sinergise.sentinel.byoctool.cli.IngestCmd;
import com.sinergise.sentinel.byoctool.cli.ListTilesCmd;
import com.sinergise.sentinel.byoctool.cli.SetCoverageCmd;
import com.sinergise.sentinel.byoctool.ingestion.storage.GCStorageClient;
import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;
import com.sinergise.sentinel.byoctool.ingestion.storage.S3StorageClient;
import com.sinergise.sentinel.byoctool.sentinelhub.AuthClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocDeployment;
import com.sinergise.sentinel.byoctool.sentinelhub.GlobalByocClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollectionInfo;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.core.retry.backoff.EqualJitterBackoffStrategy;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.Duration;

@Command(
    name = "byoc-tool",
    version = ByocTool.VERSION,
    description =
        "Utility tool for Sentinel Hub BYOC service.",
    mixinStandardHelpOptions = true,
    subcommands = {ListTilesCmd.class, SetCoverageCmd.class, IngestCmd.class, HelpCommand.class})
@Log4j2
public class ByocTool implements Runnable {

  public static final String VERSION = "0.8.4";

  @ArgGroup(exclusive = false)
  private AuthCredentials authCredentials;

  private static class AuthCredentials {
    @Option(
        names = {"--auth-client-id"},
        description =
            "Sentinel Hub auth client id. Can also be provided in the environment variable SH_CLIENT_ID",
        required = true)
    String clientId;

    @Option(
        names = {"--auth-client-secret"},
        description =
            "Sentinel Hub auth client secret. Can also be provided in the environment variable SH_CLIENT_SECRET",
        required = true)
    String clientSecret;
  }

  @ArgGroup(exclusive = false)
  private AwsCredentials awsCredentials;
  @ArgGroup(exclusive = false)
  private GcpCredentials gcpCredentials;

  private static class AwsCredentials {
    @Option(
        names = {"--aws-access-key-id"},
        description =
            "AWS access key id. Can also be provided in another ways. Check here https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html")
    private String accessKey;

    @Option(
        names = {"--aws-secret-access-key"},
        description =
            "AWS secret access key. Can also be provided in another ways. Check here https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html")
    private String secretKey;
  }

  private static class GcpCredentials {
    @Option(
        names = {"--gcp-key-file"},
        description =
            "GCP service account key file. Check here https://cloud.google.com/iam/docs/creating-managing-service-account-keys")
    private String keyFilePath;
  }

  private AuthClient authClient;

  @Override
  public void run() {
  }

  protected AuthClient newAuthClient() {
    if (authCredentials != null) {
      return new AuthClient(authCredentials.clientId, authCredentials.clientSecret);
    } else {
      return new AuthClient();
    }
  }

  protected AuthClient getAuthClient() {
    if (authClient == null) {
      authClient = newAuthClient();
    }

    return authClient;
  }

  public ByocClient newByocClient(ByocDeployment deployment) {
    return ByocClient.newByocClient(getAuthClient(), deployment);
  }

  public ByocClient newByocClient(String collectionId) {
    ByocCollectionInfo collectionInfo = getCollectionInfo(collectionId);
    return newByocClient(collectionInfo.getDeployment());
  }

  public ByocCollectionInfo getCollectionInfo(String collectionId) {
    return new GlobalByocClient(getAuthClient())
        .getCollectionInfo(collectionId)
        .orElseThrow(() -> new RuntimeException("Collection not found."));
  }

  public ObjectStorageClient newObjectStorageClient(ByocCollectionInfo collectionInfo) {
    if (gcpCredentials != null) {
      try {
        GoogleCredentials credentials = GoogleCredentials.fromStream(new FileInputStream(gcpCredentials.keyFilePath));
        Storage storage = StorageOptions.newBuilder().setCredentials(credentials).build().getService();
        return new GCStorageClient(storage);
      } catch (IOException e) {
        throw new RuntimeException("Unable to create gcs storage client.", e);
      }
    } else {
      return new S3StorageClient(newS3Client(collectionInfo.getS3Region()));
    }
  }

  private S3Client newS3Client(Region region) {
    S3ClientBuilder s3ClientBuilder = S3Client.builder();

    if (awsCredentials != null) {
      s3ClientBuilder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(awsCredentials.accessKey, awsCredentials.secretKey)));
    }

    RetryPolicy retryPolicy = RetryPolicy.builder()
        .numRetries(10)
        .backoffStrategy(EqualJitterBackoffStrategy.builder()
            .baseDelay(Duration.ofSeconds(1))
            .maxBackoffTime(Duration.ofMinutes(10))
            .build())
        .build();

    return s3ClientBuilder.region(region)
        .overrideConfiguration(ClientOverrideConfiguration.builder()
            .retryPolicy(retryPolicy)
            .build())
        .build();
  }

  public static void main(String... args) {
    CommandLine cmd = new CommandLine(new ByocTool());
    cmd.setExecutionStrategy(new RunAll());
    cmd.setExecutionExceptionHandler(
        (e, commandLine, parseResult) -> {
          log.error("Error occurred with message \"{}\"", e.getMessage(), e);
          return 1;
        });

    if (args.length == 0) {
      cmd.execute("help");
    } else {
      cmd.execute(args);
    }
  }
}
