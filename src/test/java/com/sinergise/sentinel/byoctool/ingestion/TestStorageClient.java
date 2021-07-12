package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;

import java.io.InputStream;
import java.nio.file.Path;

public class TestStorageClient implements ObjectStorageClient {
    @Override
    public void store(String bucketName, String objectKey, Path localCogPath) {

    }

    @Override
    public InputStream getObjectAsStream(String bucketName, String key) {
        return null;
    }

    @Override
    public void downloadObject(String bucketName, String key, Path target) {

    }

    @Override
    public void close() {

    }
}
