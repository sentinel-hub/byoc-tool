package com.sinergise.sentinel.byoctool.ingestion.storage;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;

import java.io.InputStream;
import java.nio.file.Path;

public interface ObjectStorageClient {
    void store(ByocCollection collection, Path localCogPath, String objectKey);
    InputStream getObjectAsStream(ByocCollection collection, String key);
    void close();
}
