package com.ssafy.backend.global.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 파일 저장 추상화 인터페이스. PROFILE_IMG 환경변수로 구현체를 전환한다.
 * - FILE → LocalFileStorageService (로컬 디스크)
 * - S3   → S3FileStorageService (AWS S3)
 */
public interface FileStorageService {

    /**
     * 파일을 지정 디렉터리에 저장하고 접근 가능한 URL을 반환한다.
     *
     * @param file      업로드할 파일
     * @param directory 저장 하위 디렉터리 (예: "profile-images")
     * @return 완전한 접근 URL (로컬: http://host/files/profile-images/xxx.jpg, S3: https://bucket.s3.../xxx.jpg)
     */
    String upload(MultipartFile file, String directory);

    /**
     * 저장된 파일을 삭제한다. URL이 이 구현체 소관이 아니면 무시한다.
     *
     * @param fileUrl upload()가 반환한 접근 URL
     */
    void delete(String fileUrl);
}