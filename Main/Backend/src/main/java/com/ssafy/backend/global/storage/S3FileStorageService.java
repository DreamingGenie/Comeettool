package com.ssafy.backend.global.storage;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

/**
 * AWS S3 파일 저장 구현체. PROFILE_IMG=S3일 때 활성화된다.
 * 자격증명은 AWS 기본 체인에서 자동 로드한다:
 *   1순위: 환경변수 AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
 *   2순위: EC2 IAM 역할 (배포 환경 권장)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "profile-img", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    private S3Client s3Client;

    @PostConstruct
    void init() {
        s3Client = S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Override
    public String upload(MultipartFile file, String directory) {
        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : "";
        String key = directory + "/" + UUID.randomUUID() + ext;

        try {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException e) {
            log.error("S3 파일 업로드 실패: {}", e.getMessage());
            throw new CustomException(ErrorCode.INTERNAL_ERROR);
        }

        return "https://" + bucket + ".s3." + region + ".amazonaws.com/" + key;
    }

    @Override
    public void delete(String fileUrl) {
        String prefix = "https://" + bucket + ".s3." + region + ".amazonaws.com/";
        if (fileUrl == null || !fileUrl.startsWith(prefix)) {
            return; // 이 구현체가 생성한 URL이 아니면 무시
        }
        String key = fileUrl.substring(prefix.length());
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
        } catch (Exception e) {
            log.warn("S3 파일 삭제 실패 — key: {}", key);
        }
    }
}