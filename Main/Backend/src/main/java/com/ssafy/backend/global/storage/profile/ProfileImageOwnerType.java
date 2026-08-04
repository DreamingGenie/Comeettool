package com.ssafy.backend.global.storage.profile;

import java.util.Arrays;

public enum ProfileImageOwnerType {

    USER("users"),
    TEAM("teams");

    private final String pathSegment;

    ProfileImageOwnerType(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    public String pathSegment() {
        return pathSegment;
    }

    public static ProfileImageOwnerType fromPathSegment(String pathSegment) {
        return Arrays.stream(values())
                .filter(type -> type.pathSegment.equals(pathSegment))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 프로필 소유자 유형입니다."));
    }
}
