package com.ssafy.backend.global.storage;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;

/**
 * 비공개 S3 Bucket을 사용하는 공통 Object Storage 구현체.
 * 공개 URL 대신 검증된 Key와 메타데이터만 반환한다.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "S3")
public class S3ObjectStorageService implements ObjectStorageService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3Client;
    private final String bucket;

    public S3ObjectStorageService(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Client = Objects.requireNonNull(s3Client, "S3Client가 필요합니다.");
        this.bucket = requireText(bucket, "S3 Bucket 이름이 필요합니다.");
    }

    @Override
    public StoredObject upload(StorageUploadRequest request, InputStream content) {
        Objects.requireNonNull(request, "스토리지 업로드 요청이 필요합니다.");
        Objects.requireNonNull(content, "업로드할 스트림이 필요합니다.");

        StorageObjectKey objectKey = request.objectKey();
        PutObjectRequest.Builder builder = PutObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey.value())
                .contentType(request.contentType());
        if (request.cacheControl() != null) {
            builder.cacheControl(request.cacheControl());
        }

        try {
            s3Client.putObject(
                    builder.build(),
                    RequestBody.fromInputStream(content, request.contentLength())
            );
            return new StoredObject(
                    objectKey.value(),
                    request.contentType(),
                    request.contentLength()
            );
        } catch (SdkException e) {
            throw storageFailure("업로드", objectKey, e);
        }
    }

    @Override
    public StoredObject metadata(StorageObjectKey objectKey) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");

        try {
            HeadObjectResponse response = s3Client.headObject(
                    HeadObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey.value())
                            .build()
            );
            return new StoredObject(
                    objectKey.value(),
                    defaultContentType(response.contentType()),
                    response.contentLength()
            );
        } catch (S3Exception e) {
            throw mapReadFailure("메타데이터 조회", objectKey, e);
        } catch (SdkException e) {
            throw storageFailure("메타데이터 조회", objectKey, e);
        }
    }

    @Override
    public void download(StorageObjectKey objectKey, OutputStream target) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");
        Objects.requireNonNull(target, "다운로드 출력 스트림이 필요합니다.");

        try (ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                GetObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey.value())
                        .build()
        )) {
            response.transferTo(target);
        } catch (S3Exception e) {
            throw mapReadFailure("다운로드", objectKey, e);
        } catch (IOException | SdkException e) {
            throw storageFailure("다운로드", objectKey, e);
        }
    }

    @Override
    public void delete(StorageObjectKey objectKey) {
        Objects.requireNonNull(objectKey, "스토리지 객체 Key가 필요합니다.");

        try {
            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey.value())
                            .build()
            );
        } catch (SdkException e) {
            throw storageFailure("삭제", objectKey, e);
        }
    }

    private CustomException mapReadFailure(
            String operation,
            StorageObjectKey objectKey,
            S3Exception exception
    ) {
        if (exception.statusCode() == 404) {
            return new CustomException(ErrorCode.NOT_FOUND);
        }
        return storageFailure(operation, objectKey, exception);
    }

    private CustomException storageFailure(
            String operation,
            StorageObjectKey objectKey,
            Exception exception
    ) {
        log.error(
                "S3 객체 {} 실패: directory={}, cause={}",
                operation,
                objectKey.directory().prefix(),
                exception.getClass().getSimpleName()
        );
        return new CustomException(ErrorCode.INTERNAL_ERROR);
    }

    private static String defaultContentType(String contentType) {
        return contentType == null || contentType.isBlank()
                ? DEFAULT_CONTENT_TYPE
                : contentType;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
