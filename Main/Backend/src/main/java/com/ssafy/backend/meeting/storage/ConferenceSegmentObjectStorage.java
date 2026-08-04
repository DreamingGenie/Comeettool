package com.ssafy.backend.meeting.storage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.StorageObjectKey;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

/**
 * MEET-12 VAD 세그먼트 저장소.
 * 공통 S3 bucket({@code aws.s3.bucket})과 AWS 기본 자격증명 체인(SSO/IAM Role)을 사용한다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "S3")
public class ConferenceSegmentObjectStorage {

    private final S3Client s3Client;
    private final String bucket;

    public ConferenceSegmentObjectStorage(
            S3Client s3Client,
            @Value("${aws.s3.bucket}") String bucket
    ) {
        this.s3Client = s3Client;
        this.bucket = requireText(bucket, "AWS S3 bucket 이름이 필요합니다.");
    }

    public boolean exists(StorageObjectKey objectKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey.value())
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw storageFailed(e);
        }
    }

    public Optional<byte[]> getBytes(StorageObjectKey objectKey) {
        try {
            return Optional.of(s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey.value())
                    .build()).asByteArray());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw storageFailed(e);
        }
    }

    public boolean putIfAbsent(StorageObjectKey objectKey, byte[] bytes, String contentType) {
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(objectKey.value())
                            .contentType(contentType)
                            .ifNoneMatch("*")
                            .build(),
                    RequestBody.fromBytes(bytes)
            );
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 412) {
                return false;
            }
            throw storageFailed(e);
        }
    }

    public List<String> listKeys(String prefix) {
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            var response = s3Client.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefix)
                    .continuationToken(token)
                    .build());
            response.contents().forEach(obj -> keys.add(obj.key()));
            token = Boolean.TRUE.equals(response.isTruncated())
                    ? response.nextContinuationToken()
                    : null;
        } while (token != null);
        return keys;
    }

    private CustomException storageFailed(Exception e) {
        log.error("Conference segment storage failed: {}", e.getMessage());
        return new CustomException(ErrorCode.VAD_STORAGE_FAILED);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}
