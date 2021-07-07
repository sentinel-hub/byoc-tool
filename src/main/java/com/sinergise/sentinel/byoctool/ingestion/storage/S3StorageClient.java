package com.sinergise.sentinel.byoctool.ingestion.storage;

import com.sinergise.sentinel.byoctool.sentinelhub.models.ByocCollection;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;
import java.nio.file.Path;

@RequiredArgsConstructor
public class S3StorageClient implements ObjectStorageClient {
    private final S3Client s3Client;

    @Setter
    private boolean multipartUpload;

    @Override
    public void store(ByocCollection collection, Path cogPath, String s3Key) {
        if (multipartUpload) {
            S3MultiPartUploader.upload(s3Client, collection.getS3Bucket(), s3Key, cogPath);
        } else {
            PutObjectRequest request = PutObjectRequest.builder().bucket(collection.getS3Bucket()).key(s3Key).build();
            s3Client.putObject(request, RequestBody.fromFile(cogPath));
        }
    }

    @Override
    public InputStream getObjectAsStream(ByocCollection collection, String key) {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(collection.getS3Bucket()).key(key).build();
        return s3Client.getObject(request);
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
