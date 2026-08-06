package com.ssafy.backend.global.storage;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;

/**
 * storage.provider가 S3가 아닐 때 활성화되는 Object Storage 폴백.
 * 회의 녹음 등 Object Storage 기능은 AWS S3 연결을 전제로 하므로,
 * 미설정 상태에서 호출되면 명확히 실패시킨다.
 * (VadRecordingServiceImpl 등 항상 로딩되는 빈이 ObjectStorageService를 주입받아도
 *  로컬 컨텍스트가 기동되도록 보장한다.)
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "FILE", matchIfMissing = true)
public class LocalObjectStorageService implements ObjectStorageService {

    @Override
    public StoredObject upload(StorageUploadRequest request, InputStream content) {
        throw notConfigured();
    }

    @Override
    public boolean uploadIfAbsent(StorageUploadRequest request, InputStream content) {
        throw notConfigured();
    }

    @Override
    public boolean exists(StorageObjectKey objectKey) {
        throw notConfigured();
    }

    @Override
    public StoredObject metadata(StorageObjectKey objectKey) {
        throw notConfigured();
    }

    @Override
    public void download(StorageObjectKey objectKey, OutputStream target) {
        throw notConfigured();
    }

    @Override
    public void delete(StorageObjectKey objectKey) {
        throw notConfigured();
    }

    @Override
    public List<String> list(StorageDirectory directory, String... prefixSegments) {
        throw notConfigured();
    }

    private CustomException notConfigured() {
        log.error("Object Storage가 설정되지 않았습니다. storage.provider=S3와 aws.s3.* 설정이 필요합니다.");
        return new CustomException(ErrorCode.INTERNAL_ERROR);
    }
}
