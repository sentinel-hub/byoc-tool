package com.sinergise.sentinel.byoctool.ingestion.storage;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedList;

@Log4j2
@RequiredArgsConstructor
public class S3MultiPartUploader {

  private final S3Client s3Client;

  @Setter
  private int partSizeInMb = 5;

  public void upload(String bucketName, String key, File file) {
    CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
        .bucket(bucketName)
        .key(key)
        .build();

    CreateMultipartUploadResponse response = s3Client.createMultipartUpload(createMultipartUploadRequest);
    String uploadId = response.uploadId();

    LinkedList<CompletedPart> parts = new LinkedList<>();
    int bufferSize = partSizeInMb * 1024 * 1024;

    try (FileInputStream fis = new FileInputStream(file);
         BufferedInputStream bis = new BufferedInputStream(fis)) {

      byte[] buffer = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = bis.read(buffer)) > 0) {
        int partNumber = parts.size() + 1;

        UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
            .bucket(bucketName)
            .key(key)
            .uploadId(uploadId)
            .partNumber(partNumber)
            .contentMD5(bufferMD5Sum(buffer, bytesRead))
            .build();

        ByteBuffer byteBuffer = ByteBuffer.wrap(buffer, 0, bytesRead);
        UploadPartResponse uploadPartResponse = s3Client
            .uploadPart(uploadPartRequest, RequestBody.fromByteBuffer(byteBuffer));

        CompletedPart part = CompletedPart.builder()
            .partNumber(partNumber)
            .eTag(uploadPartResponse.eTag())
            .build();

        parts.add(part);
      }
    } catch (Exception ex) {
      AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
          .bucket(bucketName)
          .key(key)
          .uploadId(uploadId)
          .build();

      s3Client.abortMultipartUpload(abortRequest);
      throw new RuntimeException("Multipart upload failed!", ex);
    }

    CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
        .parts(parts)
        .build();

    CompleteMultipartUploadRequest completeMultipartUploadRequest = CompleteMultipartUploadRequest.builder()
        .bucket(bucketName)
        .key(key)
        .uploadId(uploadId)
        .multipartUpload(completedMultipartUpload)
        .build();

    s3Client.completeMultipartUpload(completeMultipartUploadRequest);
  }

  private String bufferMD5Sum(byte[] buffer, int bytesRead) throws NoSuchAlgorithmException {
    MessageDigest md = MessageDigest.getInstance("MD5");
    md.update(buffer, 0, bytesRead);

    return Base64.getEncoder().encodeToString(md.digest());
  }
}
