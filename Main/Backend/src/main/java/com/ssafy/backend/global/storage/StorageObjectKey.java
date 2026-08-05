package com.ssafy.backend.global.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 검증된 경로 세그먼트로만 구성한 Object Storage Key.
 * 호출자가 슬래시가 포함된 임의 경로를 직접 전달하지 못하게 한다.
 */
public record StorageObjectKey(
        StorageDirectory directory,
        List<String> pathSegments
) {

    public StorageObjectKey {
        Objects.requireNonNull(directory, "스토리지 디렉터리가 필요합니다.");
        Objects.requireNonNull(pathSegments, "스토리지 경로가 필요합니다.");
        if (pathSegments.isEmpty()) {
            throw new IllegalArgumentException("스토리지 경로는 한 개 이상의 세그먼트가 필요합니다.");
        }

        pathSegments = pathSegments.stream()
                .peek(directory::validatePathSegment)
                .toList();
    }

    public static StorageObjectKey of(StorageDirectory directory, String... pathSegments) {
        Objects.requireNonNull(pathSegments, "스토리지 경로가 필요합니다.");
        return new StorageObjectKey(directory, Arrays.asList(pathSegments));
    }

    public String value() {
        return directory.prefix() + "/" + String.join("/", pathSegments);
    }

    public String objectName() {
        return pathSegments.getLast();
    }
}
