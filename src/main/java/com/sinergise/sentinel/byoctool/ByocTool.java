package com.sinergise.sentinel.byoctool;

import com.sinergise.sentinel.byoctool.cli.IngestCmd;
import com.sinergise.sentinel.byoctool.cli.ListTilesCmd;
import com.sinergise.sentinel.byoctool.cli.SetCoverageCmd;
import com.sinergise.sentinel.byoctool.sentinelhub.AuthClient;
import com.sinergise.sentinel.byoctool.sentinelhub.ByocClient;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.*;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Command(
    name = "byoc-tool",
    version = ByocTool.VERSION,
    description =
        "Utility tool for Sentinel Hub BYOC service."
            + "If you are accessing AWS S3, then provide credentials in the environment variables "
            + "AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY.",
    mixinStandardHelpOptions = true,
    subcommands = {ListTilesCmd.class, SetCoverageCmd.class, IngestCmd.class, HelpCommand.class})
@Log4j2
public class ByocTool implements Runnable {

  public static final String VERSION = "v0.2.0";

  private ByocClient byocClient;

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

  private static class AwsCredentials {
    @Option(
        names = {"--aws-access-key-id"},
        description =
            "AWS access key id. Can also be provided in another ways. Check here https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html")
    private String accessKey;

    @Option(
        names = {"--aws-secret-access-key"},
        description =
            "Sentinel Hub auth client secret. Can also be provided in another ways. Check here https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html")
    private String secretKey;
  }

  @Override
  public void run() {}

  public ByocClient newByocClient() {
    if (byocClient == null) {
      byocClient = new ByocClient(newAuthClient());
    }

    return byocClient;
  }

  private AuthClient newAuthClient() {
    if (authCredentials != null) {
      return new AuthClient(authCredentials.clientId, authCredentials.clientSecret);
    } else {
      return new AuthClient();
    }
  }

  public S3Client newS3Client(Region region) {
    S3ClientBuilder s3ClientBuilder = S3Client.builder();

    if (awsCredentials != null) {
      s3ClientBuilder.credentialsProvider(
          StaticCredentialsProvider.create(
              AwsBasicCredentials.create(awsCredentials.accessKey, awsCredentials.secretKey)));
    }

    return s3ClientBuilder.region(region).build();
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
