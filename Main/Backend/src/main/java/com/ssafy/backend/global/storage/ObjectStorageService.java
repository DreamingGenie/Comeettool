package com.ssafy.backend.global.storage;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * S3 같은 Object Storage의 공통 계약.
 * 대용량 회의 음성을 메모리에 모두 올리지 않도록 스트림 기반으로 처리한다.
 */
public interface ObjectStorageService {

    StoredObject upload(StorageUploadRequest request, InputStream content);

    /**
     * 객체가 존재하지 않을 때만 생성한다(멱등 write). 조건부 PUT(If-None-Match)을 사용한다.
     *
     * @return 새로 생성했으면 true, 이미 존재해 생성하지 않았으면 false
     */
    boolean uploadIfAbsent(StorageUploadRequest request, InputStream content);

    boolean exists(StorageObjectKey objectKey);

    StoredObject metadata(StorageObjectKey objectKey);

    void download(StorageObjectKey objectKey, OutputStream target);

    void delete(StorageObjectKey objectKey);

    /**
     * 주어진 디렉터리·경로 세그먼트 하위에 존재하는 객체들의 파일명(마지막 세그먼트) 목록을 반환한다.
     * 예: {@code list(CONFERENCES, "3", "participants", "12")} → ["segment-000001.json", ...]
     */
    List<String> list(StorageDirectory directory, String... prefixSegments);
}
