package com.figma.design.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinIOConfig {

    @Value("${minio.endpoint:${MINIO_ENDPOINT:http://localhost:9000}}")
    private String minioUrl;

    @Value("${minio.access-key:${MINIO_ROOT_USER:minioadmin}}")
    private String accessKey;

    @Value("${minio.secret-key:${MINIO_ROOT_PASSWORD:minioadmin}}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(minioUrl)
                .credentials(accessKey, secretKey)
                .build();
    }
}
