package com.ssafy.backend.global.storage;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 로컬 디스크 파일 저장 구현체. PROFILE_IMG=FILE(기본값)일 때 활성화된다.
 * 업로드 디렉터리는 WebMvcConfig에서 /files/** 경로로 정적 서빙한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "profile-img", havingValue = "FILE")
public class LocalFileStorageService implements FileStorageService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.base-url}")
    private String baseUrl;

    @Override
    public String upload(MultipartFile file, String directory) {
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String filename = UUID.randomUUID() + ext;

        Path targetDir = Paths.get(uploadDir, directory);
        try {
            Files.createDirectories(targetDir);
            Files.copy(file.getInputStream(), targetDir.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("파일 저장 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        return baseUrl + "/files/" + directory + "/" + filename;
    }

    @Override
    public void delete(String fileUrl) {
        String prefix = baseUrl + "/files/";
        if (fileUrl == null || !fileUrl.startsWith(prefix)) {
            return; // 이 구현체가 생성한 URL이 아니면 무시
        }
        String relativePath = fileUrl.substring(prefix.length());
        Path filePath = Paths.get(uploadDir, relativePath);
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // 파일 삭제 실패는 업로드를 막지 않는다 — 로그만 기록
            log.warn("기존 파일 삭제 실패: {}", filePath);
        }
    }
}