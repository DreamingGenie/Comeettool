package com.ssafy.backend.global.storage.profile;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.ObjectStorageService;
import com.ssafy.backend.global.storage.StorageObjectKey;
import com.ssafy.backend.global.storage.StorageUploadRequest;
import com.ssafy.backend.global.storage.StoredObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@ConditionalOnProperty(name = "profile-img", havingValue = "S3")
public class S3ProfileImageStorageService implements ProfileImageStorageService {

    private final ObjectStorageService objectStorageService;
    private final String publicBaseUrl;

    public S3ProfileImageStorageService(
            ObjectStorageService objectStorageService,
            @Value("${file.base-url}") String publicBaseUrl
    ) {
        this.objectStorageService = objectStorageService;
        this.publicBaseUrl = stripTrailingSlash(
                requireText(publicBaseUrl, "파일 공개 Base URL이 필요합니다.")
        );
    }

    @Override
    public String upload(MultipartFile file, ProfileImageOwner owner) {
        ProfileImagePolicy.PreparedProfileImage prepared = ProfileImagePolicy.prepare(file);
        StorageObjectKey objectKey = ProfileImageObjectKey.scoped(owner, prepared.filename());
        StorageUploadRequest request = new StorageUploadRequest(
                objectKey,
                prepared.contentLength(),
                prepared.contentType(),
                ProfileImagePolicy.CACHE_CONTROL
        );

        try (InputStream content = file.getInputStream()) {
            objectStorageService.upload(request, content);
        } catch (IOException e) {
            log.error("프로필 이미지 입력 스트림 처리 실패: {}", e.getClass().getSimpleName());
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        return publicBaseUrl + "/files/" + objectKey.value();
    }

    @Override
    public void delete(String fileUrl) {
        ProfileImageObjectKey.fromPublicUrl(publicBaseUrl, fileUrl)
                .ifPresent(this::deleteWithoutBreakingProfileUpdate);
    }

    public ProfileImageContent download(
            ProfileImageOwner owner,
            String filename
    ) {
        try {
            return download(ProfileImageObjectKey.scoped(owner, filename));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
    }

    public ProfileImageContent downloadLegacy(String filename) {
        try {
            return download(ProfileImageObjectKey.legacy(filename));
        } catch (IllegalArgumentException exception) {
            throw new CustomException(ErrorCode.NOT_FOUND);
        }
    }

    private ProfileImageContent download(StorageObjectKey objectKey) {
        StoredObject metadata = objectStorageService.metadata(objectKey);
        if (metadata.contentLength() > ProfileImagePolicy.MAX_BYTES) {
            log.error("허용 크기를 초과한 S3 프로필 이미지가 조회됨");
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        ByteArrayOutputStream content = new ByteArrayOutputStream(
                Math.toIntExact(metadata.contentLength())
        );
        objectStorageService.download(objectKey, content);
        return new ProfileImageContent(
                content.toByteArray(),
                ProfileImagePolicy.safeContentType(metadata.contentType())
        );
    }

    private void deleteWithoutBreakingProfileUpdate(StorageObjectKey objectKey) {
        try {
            objectStorageService.delete(objectKey);
        } catch (CustomException e) {
            log.warn("기존 S3 프로필 이미지 삭제 실패: {}", e.getErrorCode().name());
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
