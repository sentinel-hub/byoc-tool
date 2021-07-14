package com.sinergise.sentinel.byoctool.ingestion.storage;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;

@RequiredArgsConstructor
@Log4j2
public class S3StorageClient implements ObjectStorageClient {

  private final S3Client s3Client;

  @Setter
  private boolean multipartUpload;

  @Override
  public void store(String bucketName, String objectKey, byte[] data) {
    for (int i = 0; i < 3; i++) {
      try {
        log.debug("Uploading data to {}", objectKey);
        new S3SinglePartUploader(s3Client).upload(bucketName, objectKey, data);
        log.debug("Done uploading data to {}", objectKey);
        return;
      } catch (Exception ex) {
        log.error("Failed to upload data.", ex);
      }
    }
    throw new RuntimeException(String.format(
            "Failed to upload data to %s too many times. Giving up.", objectKey));
  }

  @Override
  public void store(String bucketName, String s3Key, Path path) {
    // Retry upload if integrity check fails.
    // Other errors (related to connection) are handled by AWS SDK.
    for (int i = 0; i < 3; i++) {
      try {
        log.debug("Uploading file {} to {}", path, s3Key);

        if (multipartUpload) {
          new S3MultiPartUploader(s3Client).upload(bucketName, s3Key, path.toFile());
        } else {
          new S3SinglePartUploader(s3Client).upload(bucketName, s3Key, path.toFile());
        }

        log.debug("Done uploading file {} to {}", path, s3Key);
        return;
      } catch (Exception ex) {
        log.error("Failed to upload file {}", path, ex);
      }
    }
    throw new RuntimeException(String.format(
        "Failed to upload file %s too many times. Giving up.", path));
  }

  @Override
  public InputStream getObjectAsStream(String bucketName, String key) {
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    return s3Client.getObject(request);
  }

  @Override
  public void downloadObject(String bucketName, String key, Path target) {
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    s3Client.getObject(request, ResponseTransformer.toFile(target));
  }

  @Override
  public void close() {
    s3Client.close();
  }
}
