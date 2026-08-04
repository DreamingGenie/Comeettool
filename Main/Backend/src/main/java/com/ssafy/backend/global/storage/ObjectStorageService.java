package com.ssafy.backend.global.storage;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * S3 같은 Object Storage의 공통 계약.
 * 대용량 회의 음성을 메모리에 모두 올리지 않도록 스트림 기반으로 처리한다.
 */
public interface ObjectStorageService {

    StoredObject upload(StorageUploadRequest request, InputStream content);

    StoredObject metadata(StorageObjectKey objectKey);

    void download(StorageObjectKey objectKey, OutputStream target);

    void delete(StorageObjectKey objectKey);
}
