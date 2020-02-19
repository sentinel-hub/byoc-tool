package byoc.ingestion;

import java.nio.file.Path;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3Uploader implements AutoCloseable {

  private final S3Client s3;

  S3Uploader(S3Client s3) {
    this.s3 = s3;
  }

  void uploadWithRetry(String bucket, String key, Path path) {
    int i = 0;

    while (true) {
      try {
        s3.putObject(
            PutObjectRequest.builder().bucket(bucket).key(key).build(), RequestBody.fromFile(path));

        return;
      } catch (Exception e) {
        if (i++ > 2) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void close() {}
}
