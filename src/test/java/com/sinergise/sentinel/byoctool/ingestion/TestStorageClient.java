package com.sinergise.sentinel.byoctool.ingestion;

import com.sinergise.sentinel.byoctool.ingestion.storage.ObjectStorageClient;
import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;

import java.io.InputStream;
import java.nio.file.Path;

public class TestStorageClient implements ObjectStorageClient {
    @Override
    public void store(ByocCollection collection, Path localCogPath, String objectKey) {

    }

    @Override
    public InputStream getObjectAsStream(ByocCollection collection, String key) {
        return null;
    }

    @Override
    public void close() {

    }
}
