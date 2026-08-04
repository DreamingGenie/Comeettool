package com.ssafy.backend.global.storage;

/**
 * 저장 객체의 본문을 제외한 메타데이터.
 * 애플리케이션과 DB에는 공개 URL 대신 key를 보관한다.
 */
public record StoredObject(
        String key,
        String contentType,
        long contentLength
) {

    public StoredObject {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("스토리지 객체 Key가 필요합니다.");
        }
        if (contentType == null || contentType.isBlank()) {
            throw new IllegalArgumentException("Content-Type이 필요합니다.");
        }
        if (contentLength < 0) {
            throw new IllegalArgumentException("파일 크기는 0 이상이어야 합니다.");
        }
        key = key.trim();
        contentType = contentType.trim();
    }
}
