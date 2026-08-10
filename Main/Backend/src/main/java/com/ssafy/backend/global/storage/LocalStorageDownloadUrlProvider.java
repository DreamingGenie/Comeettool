package com.ssafy.backend.global.storage;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/** 로컬 파일 저장 모드에서 기존 /files/** 정적 리소스 URL을 생성한다. */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "FILE", matchIfMissing = true)
public class LocalStorageDownloadUrlProvider implements StorageDownloadUrlProvider {

    private final String publicBaseUrl;

    public LocalStorageDownloadUrlProvider(@Value("${file.base-url}") String publicBaseUrl) {
        String baseUrl = requireText(publicBaseUrl, "파일 공개 Base URL이 필요합니다.");
        this.publicBaseUrl = baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1)
                : baseUrl;
    }

    @Override
    public String createDownloadUrl(StorageObjectKey objectKey, String contentType) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        return publicBaseUrl + "/files/" + objectKey.value();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
