package com.sinergise.sentinel.byoctool.ingestion.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@RequiredArgsConstructor
public class GCStorageClient implements ObjectStorageClient {

  private final Storage storage;

  @Override
  public void store(String bucketName, String objectKey, Path localCogPath) {
    BlobId blobId = BlobId.of(bucketName, objectKey);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    try {
      storage.createFrom(blobInfo, localCogPath);
    } catch (IOException ex) {
      throw new RuntimeException("Failed to store " + localCogPath + " to: gs://" + bucketName + "/" + objectKey, ex);
    }
  }

  @Override
  public void store(String bucketName, String objectKey, byte[] data) {
    BlobId blobId = BlobId.of(bucketName, objectKey);
    BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
    try {
      storage.createFrom(blobInfo, new ByteArrayInputStream(data));
    } catch (IOException ex) {
      throw new RuntimeException("Failed to store data to: gs://"+ bucketName+"/"+objectKey, ex);
    }
  }


  @Override
  public InputStream getObjectAsStream(String bucketName, String key) {

    BlobId objectId = BlobId.of(bucketName, key);
    try {
      Blob blob = storage.get(objectId);
      return new ByteArrayInputStream(blob.getContent());
    } catch (Exception ex) {
      throw new RuntimeException("Failed to read from: gs://" + bucketName + "/" + key, ex);
    }
  }

  @Override
  public void downloadObject(String bucketName, String key, Path target) {
    BlobId objectId = BlobId.of(bucketName, key);
    try {
      Blob blob = storage.get(objectId);
      blob.downloadTo(target);
    } catch (Exception ex) {
      throw new RuntimeException("Failed to download: gs://" + bucketName + "/" + key, ex);
    }
  }

  @Override
  public void close() {
  }
}
