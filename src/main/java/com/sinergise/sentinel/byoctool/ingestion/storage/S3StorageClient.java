package com.sinergise.sentinel.byoctool.ingestion.storage;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
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
    public void store(String bucketName, Path cogPath, String s3Key) {
        if (multipartUpload) {
            S3MultiPartUploader.upload(s3Client, bucketName, s3Key, cogPath);
        } else {
            PutObjectRequest request = PutObjectRequest.builder().bucket(bucketName).key(s3Key).build();
            s3Client.putObject(request, RequestBody.fromFile(cogPath));
        }
    }

    @Override
    public InputStream getObjectAsStream(String bucketName, String key) {
        GetObjectRequest request =
                GetObjectRequest.builder().bucket(bucketName).key(key).build();
        return s3Client.getObject(request);
    }

    @Override
    public void downloadObject(String bucketName, String key, Path target) {
        s3Client.getObject(r -> r.bucket(bucketName).key(key), ResponseTransformer.toFile(target));
    }

    @Override
    public void close() {
        s3Client.close();
    }
}
