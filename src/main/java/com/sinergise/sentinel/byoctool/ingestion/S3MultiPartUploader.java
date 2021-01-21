package com.sinergise.sentinel.byoctool.ingestion;

import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

@Log4j2
@Builder
public class S3MultiPartUploader {

  private final S3Client s3;

  @Builder.Default
  private final int bufferSize = 5 * 1024 * 1024;

  void upload(String bucketName, String key, Path filePath) {
    try (InputStream fis = Files.newInputStream(filePath)) {
      upload(bucketName, key, fis);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  void upload(String bucketName, String key, InputStream stream) {
    MultiPartUpload upload = new MultiPartUpload(bucketName, key, stream);

    Thread thread = new Thread(upload::abortUpload);
    Runtime.getRuntime().addShutdownHook(thread);
    upload.upload();
    Runtime.getRuntime().removeShutdownHook(thread);
  }

  class MultiPartUpload {

    private final String bucketName;
    private final String key;
    private final InputStream stream;
    private final ArrayList<CompletedPart> parts = new ArrayList<>();

    private String uploadId;
    private boolean completed;
    private boolean abortInitiated;

    MultiPartUpload(String bucketName, String key, InputStream stream) {
      this.bucketName = bucketName;
      this.key = key;
      this.stream = stream;
    }

    void upload() {
      try {
        createUpload();
        uploadParts();
        completeUpload();
      } catch (Exception ex) {
        abortUpload();
        throw new RuntimeException("Multipart upload failed due to: " + ex.getMessage(), ex);
      }
    }

    private synchronized void createUpload() {
      if (abortInitiated) {
        return;
      }

      CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .build();

      CreateMultipartUploadResponse response = s3.createMultipartUpload(createMultipartUploadRequest);

      uploadId = response.uploadId();

      log.info("Creating multipart upload {}", uploadId);
    }

    private void uploadParts() throws IOException {
      try (BufferedInputStream bis = new BufferedInputStream(stream)) {
        byte[] buffer = new byte[bufferSize];
        int bytesRead;
        while ((bytesRead = bis.read(buffer)) > 0) {
          int partNumber = parts.size() + 1;
          ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);

          synchronized (this) {
            if (abortInitiated) {
              break;
            }
            uploadPart(partNumber, byteBuffer);
          }
        }
      }
    }

    private synchronized void uploadPart(int partNumber, ByteBuffer byteBuffer) {
      UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .partNumber(partNumber)
          .build();

      UploadPartResponse uploadResponse = s3.uploadPart(uploadPartRequest, RequestBody.fromByteBuffer(byteBuffer));

      CompletedPart part = CompletedPart.builder()
          .partNumber(partNumber)
          .eTag(uploadResponse.eTag())
          .build();

      parts.add(part);
    }

    private synchronized void completeUpload() {
      if (abortInitiated) {
        return;
      }

      CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
          .parts(parts)
          .build();

      CompleteMultipartUploadRequest completeMultipartUploadRequest =
          CompleteMultipartUploadRequest.builder()
              .bucket(bucketName)
              .key(key)
              .uploadId(uploadId)
              .multipartUpload(completedMultipartUpload)
              .build();

      s3.completeMultipartUpload(completeMultipartUploadRequest);

      completed = true;
    }

    private synchronized void abortUpload() {
      abortInitiated = true;

      if (uploadId == null || completed) {
        return;
      }

      log.info("Aborting multipart upload {}", uploadId);

      AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .build();

      try {
        s3.abortMultipartUpload(abortRequest);
        log.info("Multipart upload aborted: " + uploadId);
      } catch (Exception ex) {
        log.error("Failed to abort multipart upload {}", uploadId, ex);
      }
    }
  }

  static void upload(S3Client s3, String bucketName, String key, Path filePath) {
    S3MultiPartUploader uploader = S3MultiPartUploader.builder()
        .s3(s3)
        .build();

    uploader.upload(bucketName, key, filePath);
  }
}
