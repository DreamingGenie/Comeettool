package com.ssafy.backend.global.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * AWS S3 파일 저장 구현체 뼈대. PROFILE_IMG=S3일 때 활성화된다.
 * AWS SDK(software.amazon.awssdk:s3)를 build.gradle에 추가하고,
 * 배포 시 AWS_S3_BUCKET / AWS_S3_REGION / AWS 자격증명을 주입하여 구현한다.
 */
@Service
@ConditionalOnProperty(name = "profile-img", havingValue = "S3")
public class S3FileStorageService implements FileStorageService {

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    @Override
    public String upload(MultipartFile file, String directory) {
        // TODO: S3Client.putObject()로 업로드 후 https://{bucket}.s3.{region}.amazonaws.com/{key} 반환
        throw new UnsupportedOperationException("S3 업로드는 AWS SDK 의존성 추가 및 자격증명 설정 후 구현하세요.");
    }

    @Override
    public void delete(String fileUrl) {
        // TODO: URL에서 S3 key 추출 후 S3Client.deleteObject()로 삭제
        throw new UnsupportedOperationException("S3 삭제는 AWS SDK 의존성 추가 및 자격증명 설정 후 구현하세요.");
    }
}