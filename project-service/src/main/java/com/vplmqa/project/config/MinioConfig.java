package com.vplmqa.project.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    @Bean
    public MinioClient minioClient(
            @Value("${minio.endpoint:${MINIO_ENDPOINT:http://localhost:9005}}") String endpoint,
            @Value("${minio.access-key:${MINIO_ROOT_USER:minioadmin}}") String accessKey,
            @Value("${minio.secret-key:${MINIO_ROOT_PASSWORD:minioadmin}}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
