package com.ssafy.backend.global.storage.profile;

import com.ssafy.backend.global.storage.StorageDirectory;
import com.ssafy.backend.global.storage.StorageObjectKey;
import java.util.Objects;

public record ProfileImageOwner(
        ProfileImageOwnerType type,
        Long id
) {

    public ProfileImageOwner {
        Objects.requireNonNull(type, "프로필 소유자 유형이 필요합니다.");
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("프로필 소유자 ID는 양수여야 합니다.");
        }
    }

    public static ProfileImageOwner user(Long userId) {
        return new ProfileImageOwner(ProfileImageOwnerType.USER, userId);
    }

    public static ProfileImageOwner team(Long teamId) {
        return new ProfileImageOwner(ProfileImageOwnerType.TEAM, teamId);
    }

    public StorageObjectKey objectKey(String filename) {
        return StorageObjectKey.of(
                StorageDirectory.PROFILE_IMAGES,
                type.pathSegment(),
                id.toString(),
                filename
        );
    }
}
