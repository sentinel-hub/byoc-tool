package com.sinergise.sentinel.byoctool.ingestion.storage;

import java.io.InputStream;
import java.nio.file.Path;

public interface ObjectStorageClient {
    void store(String bucketName, String objectKey, Path localCogPath);
    InputStream getObjectAsStream(String bucketName, String key);
    void downloadObject(String bucketName, String key, Path target);
    void close();
}
