package byoc.ingestion;

import java.nio.file.Path;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class S3Upload {

  static void uploadWithRetry(S3Client s3, String bucket, String key, Path path) {
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
}
