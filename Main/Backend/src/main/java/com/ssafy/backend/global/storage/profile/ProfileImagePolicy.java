package com.ssafy.backend.global.storage.profile;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import java.util.Locale;
import java.util.UUID;
import org.springframework.web.multipart.MultipartFile;

final class ProfileImagePolicy {

    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final String CACHE_CONTROL = "public, max-age=604800, immutable";

    private ProfileImagePolicy() {
    }

    static PreparedProfileImage prepare(MultipartFile file) {
        if (file.getSize() > MAX_BYTES) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }

        String extension = supportedExtension(file.getOriginalFilename());
        return new PreparedProfileImage(
                UUID.randomUUID() + "." + extension,
                contentType(extension),
                file.getSize()
        );
    }

    static String safeContentType(String contentType) {
        if ("image/png".equalsIgnoreCase(contentType)) {
            return "image/png";
        }
        if ("image/jpeg".equalsIgnoreCase(contentType)) {
            return "image/jpeg";
        }
        return "application/octet-stream";
    }

    private static String supportedExtension(String originalFilename) {
        if (originalFilename == null) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_INVALID_TYPE);
        }

        int dot = originalFilename.lastIndexOf('.');
        String extension = dot >= 0
                ? originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT)
                : "";
        if (!extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_INVALID_TYPE);
        }
        return extension;
    }

    private static String contentType(String extension) {
        return extension.equals("png") ? "image/png" : "image/jpeg";
    }

    record PreparedProfileImage(String filename, String contentType, long contentLength) {
    }
}
