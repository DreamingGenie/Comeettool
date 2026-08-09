package com.ssafy.backend.global.storage;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLDecoder;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@DisplayName("Storage 다운로드 URL 발급 테스트")
class StorageDownloadUrlProviderTest {

    private static final StorageObjectKey OBJECT_KEY = StorageObjectKey.of(
            StorageDirectory.AI_RESULTS,
            "34",
            "minutes.pdf"
    );

    @Test
    @DisplayName("S3 모드에서는 Content-Disposition이 포함된 5분 Presigned URL을 발급한다")
    void createsS3PresignedDownloadUrl() {
        try (S3Presigner presigner = S3Presigner.builder()
                .region(Region.AP_NORTHEAST_2)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build()) {
            S3StorageDownloadUrlProvider provider =
                    new S3StorageDownloadUrlProvider(presigner, "test-bucket");

            String url = provider.createDownloadUrl(OBJECT_KEY, "application/pdf");
            String decodedUrl = URLDecoder.decode(url, UTF_8);

            assertThat(url)
                    .startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/ai-results/34/minutes.pdf?")
                    .contains("X-Amz-Expires=300")
                    .contains("X-Amz-Signature=");
            assertThat(decodedUrl)
                    .contains("response-content-type=application/pdf")
                    .contains("response-content-disposition=attachment; filename=\"minutes.pdf\"");
        }
    }

    @Test
    @DisplayName("로컬 모드에서는 기존 /files 경로를 반환한다")
    void createsLocalDownloadUrl() {
        LocalStorageDownloadUrlProvider provider =
                new LocalStorageDownloadUrlProvider("http://localhost:8080/");

        String url = provider.createDownloadUrl(OBJECT_KEY, "application/pdf");

        assertThat(url).isEqualTo("http://localhost:8080/files/ai-results/34/minutes.pdf");
    }
}
