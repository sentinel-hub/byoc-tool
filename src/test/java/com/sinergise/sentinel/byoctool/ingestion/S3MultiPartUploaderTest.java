package com.sinergise.sentinel.byoctool.ingestion;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class S3MultiPartUploaderTest {

  @Test
  void failedCreateMultiPartUploadRequest_shouldReturnNiceMessage() {
    String error = "createMultipartUpload error!";
    S3MultiPartUploader uploader = newUploader(new S3ClientStub() {

      @Override
      public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
        throw newS3Exception(error);
      }
    });

    Exception ex = Assertions.assertThrows(Exception.class, () -> uploader.upload("myBucket", "myKey", randomInputStream(10)));

    assertEquals("Multipart upload failed due to: " + error, ex.getMessage());
  }

  @Test
  void failedUploadPart_shouldReturnNiceMessage() {
    String error = "uploadPart error!";
    S3MultiPartUploader uploader = newUploader(new S3ClientStub() {

      @Override
      public UploadPartResponse uploadPart(UploadPartRequest uploadPartRequest, RequestBody requestBody) {
        throw newS3Exception(error);
      }
    });

    Exception ex = Assertions.assertThrows(Exception.class, () -> uploader.upload("myBucket", "myKey", randomInputStream(10)));

    assertEquals("Multipart upload failed due to: " + error, ex.getMessage());
  }

  @Test
  void failedCompleteMultipartUpload_shouldReturnNiceMessage() {
    String error = "retryCompleteMultipartUpload error!";
    S3MultiPartUploader uploader = newUploader(new S3ClientStub() {
      @Override
      public CompleteMultipartUploadResponse completeMultipartUpload(CompleteMultipartUploadRequest request) {
        throw newS3Exception(error);
      }
    });

    Exception ex = Assertions.assertThrows(Exception.class, () -> uploader.upload("myBucket", "myKey", randomInputStream(10)));

    assertEquals("Multipart upload failed due to: " + error, ex.getMessage());
  }

  @Test
  void failedAbortMultipartUpload_shouldReturnNiceMessage() {
    S3MultiPartUploader uploader = newUploader(new S3ClientStub() {

      @Override
      public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
        return CreateMultipartUploadResponse.builder()
            .uploadId("someUploadId")
            .build();
      }

      @Override
      public UploadPartResponse uploadPart(UploadPartRequest uploadPartRequest, RequestBody requestBody) {
        throw newS3Exception("uploadPart error!");
      }

      @Override
      public AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest request) throws SdkClientException {
        throw newS3Exception("abort error!");
      }
    });

    Exception ex = Assertions.assertThrows(Exception.class, () -> uploader.upload("myBucket", "myKey", randomInputStream(10)));

    assertEquals("Multipart upload failed due to: uploadPart error!", ex.getMessage());
  }

  @Test
  void largeFile_shouldBeUploadedInMultipleParts() {
    List<UploadPartRequest> partRequests = new LinkedList<>();

    S3Client s3 = new S3ClientStub() {

      @Override
      public UploadPartResponse uploadPart(UploadPartRequest uploadPartRequest, RequestBody requestBody) {
        partRequests.add(uploadPartRequest);
        return super.uploadPart(uploadPartRequest, requestBody);
      }
    };

    S3MultiPartUploader uploader = S3MultiPartUploader.builder()
        .s3(s3)
        .bufferSize(10)
        .build();

    uploader.upload("myBucket", "myKey", randomInputStream(100));

    assertEquals(10, partRequests.size());
    assertEquals(1, partRequests.get(0).partNumber());
    assertEquals(10, partRequests.get(9).partNumber());
  }

  private static S3MultiPartUploader newUploader(S3Client s3) {
    return S3MultiPartUploader.builder()
        .s3(s3)
        .bufferSize(10)
        .build();
  }

  private static AwsServiceException newS3Exception(String error) {
    return S3Exception.builder()
        .message(error)
        .build();
  }

  private static InputStream randomInputStream(int numberOfBytes) {
    byte[] bytes = new byte[numberOfBytes];
    new Random().nextBytes(bytes);
    return new ByteArrayInputStream(bytes);
  }
}
