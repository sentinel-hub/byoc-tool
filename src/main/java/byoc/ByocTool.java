package byoc;

import byoc.commands.IngestCmd;
import byoc.commands.ListTilesCmd;
import byoc.commands.SetCoverageCmd;
import byoc.sentinelhub.AuthService;
import byoc.sentinelhub.ByocService;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.extern.log4j.Log4j2;
import picocli.CommandLine;
import picocli.CommandLine.*;

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

  public static final String VERSION = "v0.1";

  private ByocService byocService;

  @ArgGroup(exclusive = false)
  private AuthClientCredentials clientCredentials;

  private static class AuthClientCredentials {
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

  public ByocService getByocService() {
    if (byocService == null) {
      byocService = new ByocService(getAuthService(), getS3ClientBuilder());
    }

    return byocService;
  }

  private AuthService getAuthService() {
    final String clientId;
    final String clientSecret;

    if (clientCredentials != null) {
      clientId = clientCredentials.clientId;
      clientSecret = clientCredentials.clientSecret;
    } else {
      clientId = System.getenv("SH_CLIENT_ID");
      clientSecret = System.getenv("SH_CLIENT_SECRET");
    }

    return new AuthService(clientId, clientSecret);
  }

  private AmazonS3ClientBuilder getS3ClientBuilder() {
    AmazonS3ClientBuilder s3ClientBuilder = AmazonS3ClientBuilder.standard();

    if (awsCredentials != null) {
      s3ClientBuilder.withCredentials(
          new AWSStaticCredentialsProvider(
              new BasicAWSCredentials(awsCredentials.accessKey, awsCredentials.secretKey)));
    }

    return s3ClientBuilder;
  }

  public static void main(String... args) {
    CommandLine cmd = new CommandLine(new ByocTool());
    cmd.setExecutionStrategy(new RunAll());
    cmd.setExecutionExceptionHandler(
        (e, commandLine, parseResult) -> {
          log.error("Error occurred with message \"{}\"", e.getMessage(), e);
          return 0;
        });

    if (args.length == 0) {
      cmd.execute("help");
    } else {
      cmd.execute(args);
    }
  }
}
