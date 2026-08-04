package com.ssafy.backend.global.storage;

import java.util.Objects;

/**
 * 공통 Object Storage 업로드 요청.
 * cacheControl은 객체별 응답 정책이 필요할 때만 전달한다.
 */
public record StorageUploadRequest(
        StorageObjectKey objectKey,
        long contentLength,
        String contentType,
        String cacheControl
) {

    public StorageUploadRequest {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        if (contentLength < 0) {
            throw new IllegalArgumentException("파일 크기는 0 이상이어야 합니다.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content-Type이 필요합니다.");
        }
        contentType = contentType.trim();
        cacheControl = normalizeOptional(cacheControl);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
