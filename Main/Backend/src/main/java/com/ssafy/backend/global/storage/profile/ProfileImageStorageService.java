package com.ssafy.backend.global.storage.profile;

import org.springframework.web.multipart.MultipartFile;

/**
 * 사용자와 팀 스페이스가 공유하는 프로필 이미지 저장 계약.
 */
public interface ProfileImageStorageService {

    String upload(MultipartFile file, ProfileImageOwner owner);

    void delete(String fileUrl);
}
