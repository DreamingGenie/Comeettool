package com.ssafy.backend.global.storage;

import java.util.regex.Pattern;

/**
 * S3 객체를 도메인별 Prefix로 분리한다.
 * 외부 입력으로 임의 경로를 받지 않고 서버가 선택한 값만 사용한다.
 */
public enum StorageDirectory {

    PROFILE_IMAGES("profile-images"),
    CONFERENCES("conferences"),
    TRANSCRIPTS("transcripts"),
    MEETING_MINUTES("meeting-minutes"),
    AI_RESULTS("ai-results");

    private static final Pattern SAFE_PATH_SEGMENT = Pattern.compile(
            "^[A-Za-z0-9][A-Za-z0-9._-]{0,254}$"
    );

    private final String prefix;

    StorageDirectory(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    void validatePathSegment(String segment) {
        if (segment == null || !SAFE_PATH_SEGMENT.matcher(segment).matches()) {
            throw new IllegalArgumentException("안전하지 않은 스토리지 경로 세그먼트입니다.");
        }
    }
}
