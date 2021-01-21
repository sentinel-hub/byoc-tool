package com.sinergise.sentinel.byoctool.ingestion;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

class S3ClientStub implements S3Client {

  @Override
  public CreateMultipartUploadResponse createMultipartUpload(CreateMultipartUploadRequest request) {
    return CreateMultipartUploadResponse.builder()
        .uploadId("someUploadId")
        .build();
  }

  @Override
  public UploadPartResponse uploadPart(UploadPartRequest uploadPartRequest, RequestBody requestBody) {
    return UploadPartResponse.builder().build();
  }

  @Override
  public AbortMultipartUploadResponse abortMultipartUpload(AbortMultipartUploadRequest request) {
    return AbortMultipartUploadResponse.builder().build();
  }

  @Override
  public CompleteMultipartUploadResponse completeMultipartUpload(CompleteMultipartUploadRequest request) {
    return CompleteMultipartUploadResponse.builder().build();
  }

  @Override
  public String serviceName() {
    return null;
  }

  @Override
  public void close() {

  }
}
