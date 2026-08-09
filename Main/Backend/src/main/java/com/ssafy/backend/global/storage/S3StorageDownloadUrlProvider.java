package com.ssafy.backend.global.storage;

import java.time.Duration;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 비공개 S3 객체에 대해 5분 동안 유효한 다운로드 URL을 발급한다.
 * URL 자체가 임시 자격 증명이므로 DB에는 저장하지 않고 API 응답에서만 사용한다.
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "S3")
public class S3StorageDownloadUrlProvider implements StorageDownloadUrlProvider {

    private static final Duration DOWNLOAD_URL_TTL = Duration.ofMinutes(5);

    private final S3Presigner s3Presigner;
    private final String bucket;

    public S3StorageDownloadUrlProvider(
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Presigner = Objects.requireNonNull(s3Presigner, "S3Presigner가 필요합니다.");
        this.bucket = requireText(bucket, "S3 Bucket 이름이 필요합니다.");
    }

    @Override
    public String createDownloadUrl(StorageObjectKey objectKey, String contentType) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        String safeContentType = requireText(contentType, "Content-Type이 필요합니다.");

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey.value())
                .responseContentType(safeContentType)
                .responseContentDisposition(
                        "attachment; filename=\"" + objectKey.objectName() + "\""
                )
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(DOWNLOAD_URL_TTL)
                .getObjectRequest(getObjectRequest)
                .build();

        return s3Presigner.presignGetObject(presignRequest).url().toString();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
