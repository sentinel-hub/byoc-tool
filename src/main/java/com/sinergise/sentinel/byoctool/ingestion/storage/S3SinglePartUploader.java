package com.sinergise.sentinel.byoctool.ingestion.storage;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@RequiredArgsConstructor
public class S3SinglePartUploader {

  private final S3Client s3Client;


  public void upload(String bucketName, String objectKey, byte[] data) {
    RequestBody body = RequestBody.fromBytes(data);
    PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(objectKey)
            .contentLength(body.contentLength())
            .acl(ObjectCannedACL.BUCKET_OWNER_FULL_CONTROL)
            .build();
    s3Client.putObject(request, body);
  }


  public void upload(String bucketName, String key, File file) {
    PutObjectRequest request = PutObjectRequest.builder()
        .bucket(bucketName)
        .contentMD5(fileMD5Sum(file))
        .key(key)
        .build();

    s3Client.putObject(request, RequestBody.fromFile(file));
  }

  private static String fileMD5Sum(File file) {
    try (FileInputStream fis = new FileInputStream(file);
         BufferedInputStream bis = new BufferedInputStream(fis)) {

      MessageDigest md = MessageDigest.getInstance("MD5");
      int bufferSize = 1024 * 1024;
      byte[] buffer = new byte[bufferSize];
      int bytesRead;
      while ((bytesRead = bis.read(buffer)) > 0) {
        md.update(buffer, 0, bytesRead);
      }

      return Base64.getEncoder().encodeToString(md.digest());
    } catch (IOException | NoSuchAlgorithmException e) {
      throw new RuntimeException("Failed to calculate checksum for file " + file);
    }
  }
}
