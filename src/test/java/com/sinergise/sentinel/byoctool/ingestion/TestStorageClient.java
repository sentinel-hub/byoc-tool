package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;

import java.io.InputStream;
import java.nio.file.Path;

public class TestStorageClient implements ObjectStorageClient {
    @Override
    public void store(String bucketName, Path localCogPath, String objectKey) {

    }

    @Override
    public InputStream getObjectAsStream(String bucketName, String key) {
        return null;
    }

    @Override
    public void close() {

    }
}
