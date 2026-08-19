package com.vplmqa.project.service;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ExtractionStorageService {

    private final MinioClient minioClient;
    private final String bucketName;
    private volatile boolean bucketReady;

    public ExtractionStorageService(
            MinioClient minioClient,
            @Value("${minio.bucket-name:figma-designs}") String bucketName) {
        this.minioClient = minioClient;
        this.bucketName = bucketName;
    }

    public String uploadJson(String objectPath, String json) {
        return uploadText(objectPath, json, "application/json");
    }

    public String uploadCsv(String objectPath, String csv) {
        return uploadText(objectPath, csv, "text/csv");
    }

    public String uploadPng(String objectPath, byte[] png) {
        return uploadBytes(objectPath, png, "image/png");
    }

    public String downloadText(String objectPath) {
        try (var stream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectPath)
                        .build())) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to download extraction artifact from MinIO: " + exception.getMessage(), exception);
        }
    }

    private String uploadText(String objectPath, String content, String contentType) {
        return uploadBytes(objectPath, content.getBytes(StandardCharsets.UTF_8), contentType);
    }

    private String uploadBytes(String objectPath, byte[] bytes, String contentType) {
        try {
            ensureBucket();
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectPath)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
            return objectPath;
        } catch (Exception exception) {
            throw new RuntimeException("Failed to upload extraction artifact to MinIO. Path: '" + objectPath + "'. Error: " + exception.getMessage(), exception);
        }
    }

    private synchronized void ensureBucket() throws Exception {
        if (bucketReady) {
            return;
        }
        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build())) {
            try {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            } catch (ErrorResponseException exception) {
                String code = exception.errorResponse() == null ? "" : exception.errorResponse().code();
                if (!"BucketAlreadyOwnedByYou".equals(code) && !"BucketAlreadyExists".equals(code)) {
                    throw exception;
                }
            }
        }
        bucketReady = true;
    }
}
