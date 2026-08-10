package com.ssafy.backend.global.storage;

/**
 * 비공개 Object Storage 객체를 클라이언트가 다운로드할 수 있는 URL로 변환한다.
 * S3 환경에서는 짧게 만료되는 Presigned URL을, 로컬 환경에서는 정적 파일 URL을 반환한다.
 */
public interface StorageDownloadUrlProvider {

    String createDownloadUrl(StorageObjectKey objectKey, String contentType);
}
