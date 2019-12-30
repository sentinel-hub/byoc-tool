package byoc.ingestion;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.amazonaws.services.s3.transfer.Upload;
import java.nio.file.Path;

class S3Uploader implements AutoCloseable {

  private final TransferManager transferManager;

  S3Uploader(AmazonS3 amazonS3) {
    transferManager = TransferManagerBuilder.standard().withS3Client(amazonS3).build();
  }

  void uploadWithRetry(String bucket, String key, Path path) {
    int i = 0;

    while (true) {
      try {
        Upload upload = transferManager.upload(bucket, key, path.toFile());
        upload.waitForCompletion();

        return;
      } catch (Exception e) {
        if (i++ > 2) {
          throw new RuntimeException(e);
        }
      }
    }
  }

  @Override
  public void close() {
    transferManager.shutdownNow();
  }
}
