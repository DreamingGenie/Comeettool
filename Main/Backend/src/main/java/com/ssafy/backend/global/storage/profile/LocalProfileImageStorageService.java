package com.ssafy.backend.global.storage.profile;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.StorageObjectKey;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@ConditionalOnProperty(name = "profile-img", havingValue = "FILE")
public class LocalProfileImageStorageService implements ProfileImageStorageService {

    private final Path uploadRoot;
    private final String publicBaseUrl;

    public LocalProfileImageStorageService(
            @Value("${file.upload-dir}") String uploadDir,
            @Value("${file.base-url}") String publicBaseUrl
    ) {
        this.uploadRoot = Paths.get(requireText(uploadDir, "파일 업로드 경로가 필요합니다."))
                .toAbsolutePath()
                .normalize();
        this.publicBaseUrl = stripTrailingSlash(
                requireText(publicBaseUrl, "파일 공개 Base URL이 필요합니다.")
        );
    }

    @Override
    public String upload(MultipartFile file, ProfileImageOwner owner) {
        ProfileImagePolicy.PreparedProfileImage prepared = ProfileImagePolicy.prepare(file);
        StorageObjectKey objectKey = ProfileImageObjectKey.scoped(owner, prepared.filename());
        Path target = safePath(objectKey);

        try (InputStream content = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(content, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("로컬 프로필 이미지 저장 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        return publicBaseUrl + "/files/" + objectKey.value();
    }

    @Override
    public void delete(String fileUrl) {
        ProfileImageObjectKey.fromPublicUrl(publicBaseUrl, fileUrl)
                .map(this::safePath)
                .ifPresent(this::deleteIfExists);
    }

    private Path safePath(StorageObjectKey objectKey) {
        Path target = uploadRoot.resolve(objectKey.value()).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new IllegalArgumentException("안전하지 않은 로컬 파일 경로입니다.");
        }
        return target;
    }

    private void deleteIfExists(Path target) {
        try {
            Files.deleteIfExists(target);
        } catch (IOException e) {
            log.warn("기존 로컬 프로필 이미지 삭제 실패");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
