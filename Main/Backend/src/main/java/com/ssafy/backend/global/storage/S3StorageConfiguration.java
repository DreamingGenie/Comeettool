package com.ssafy.backend.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "storage.provider", havingValue = "S3")
public class S3StorageConfiguration {

    @Bean
    public S3Client s3Client(@Value("${aws.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean(destroyMethod = "close")
    public S3Presigner s3Presigner(@Value("${aws.s3.region}") String region) {
        return S3Presigner.builder()
                .region(Region.of(region))
                .build();
    }
}
