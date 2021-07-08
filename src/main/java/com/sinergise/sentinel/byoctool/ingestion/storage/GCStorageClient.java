package com.sinergise.sentinel.byoctool.ingestion.storage;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import lombok.RequiredArgsConstructor;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

@RequiredArgsConstructor
public class GCStorageClient implements ObjectStorageClient {

    final private Storage storage;
    @Override
    public void store(ByocCollection collection, Path localCogPath, String objectKey) {
        BlobId blobId = BlobId.of(collection.getS3Bucket(), objectKey);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        try {
            storage.createFrom(blobInfo, localCogPath);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to store "+ localCogPath+ " to: gs://"+collection.getS3Bucket()+"/"+objectKey, ex);
        }
    }

    @Override
    public InputStream getObjectAsStream(ByocCollection collection, String key) {

        BlobId objectId = BlobId.of(collection.getS3Bucket(), key);
        try {
            Blob blob = storage.get(objectId);
            return new ByteArrayInputStream(blob.getContent());
        } catch (Exception ex) {
            throw new RuntimeException("Failed to read from: gs://"+collection.getS3Bucket()+"/"+key, ex);
        }
    }

    @Override
    public void close() {
    }
}
