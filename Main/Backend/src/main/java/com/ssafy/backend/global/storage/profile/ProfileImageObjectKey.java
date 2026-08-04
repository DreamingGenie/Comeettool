package com.ssafy.backend.global.storage.profile;

import com.ssafy.backend.global.storage.StorageDirectory;
import com.ssafy.backend.global.storage.StorageObjectKey;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ProfileImageObjectKey {

    private static final String FILENAME_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(?:jpg|jpeg|png)";
    private static final Pattern MANAGED_FILENAME = Pattern.compile("^" + FILENAME_PATTERN + "$");
    private static final Pattern SCOPED_KEY = Pattern.compile(
            "^profile-images/(users|teams)/([1-9][0-9]*)/(" + FILENAME_PATTERN + ")$"
    );
    private static final Pattern LEGACY_KEY = Pattern.compile(
            "^profile-images/(" + FILENAME_PATTERN + ")$"
    );

    private ProfileImageObjectKey() {
    }

    public static StorageObjectKey scoped(ProfileImageOwner owner, String filename) {
        if (!isManagedFilename(filename)) {
            throw new IllegalArgumentException("관리 대상이 아닌 프로필 이미지 파일명입니다.");
        }
        return owner.objectKey(filename);
    }

    public static StorageObjectKey legacy(String filename) {
        if (!isManagedFilename(filename)) {
            throw new IllegalArgumentException("관리 대상이 아닌 프로필 이미지 파일명입니다.");
        }
        return StorageObjectKey.of(StorageDirectory.PROFILE_IMAGES, filename);
    }

    public static Optional<StorageObjectKey> fromPublicUrl(
            String publicBaseUrl,
            String fileUrl
    ) {
        if (fileUrl == null) {
            return Optional.empty();
        }
        String prefix = stripTrailingSlash(publicBaseUrl) + "/files/";
        if (!fileUrl.startsWith(prefix)) {
            return Optional.empty();
        }
        return parse(fileUrl.substring(prefix.length()));
    }

    public static Optional<StorageObjectKey> parse(String key) {
        if (key == null) {
            return Optional.empty();
        }

        Matcher scoped = SCOPED_KEY.matcher(key);
        if (scoped.matches()) {
            try {
                ProfileImageOwner owner = new ProfileImageOwner(
                        ProfileImageOwnerType.fromPathSegment(scoped.group(1)),
                        Long.parseLong(scoped.group(2))
                );
                return Optional.of(scoped(owner, scoped.group(3)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }

        Matcher legacy = LEGACY_KEY.matcher(key);
        return legacy.matches()
                ? Optional.of(legacy(legacy.group(1)))
                : Optional.empty();
    }

    public static boolean isManagedFilename(String filename) {
        return filename != null && MANAGED_FILENAME.matcher(filename).matches();
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("파일 공개 Base URL이 필요합니다.");
        }
        String normalized = value.trim();
        int end = normalized.length();
        while (end > 0 && normalized.charAt(end - 1) == '/') {
            end--;
        }
        return normalized.substring(0, end);
    }
}
