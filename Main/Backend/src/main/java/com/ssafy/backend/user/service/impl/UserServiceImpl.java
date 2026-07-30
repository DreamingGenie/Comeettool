package com.ssafy.backend.user.service.impl;

import com.ssafy.backend.auth.service.RefreshTokenService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.jwt.JwtProvider;
import com.ssafy.backend.global.storage.FileStorageService;
import com.ssafy.backend.user.dto.RequestChangePasswordDto;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseChangePasswordDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.dto.ResponseProfileImageDto;
import com.ssafy.backend.user.dto.ResponseUserSearchDto;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.mapper.UserProfileMapper;
import com.ssafy.backend.user.repository.UserRepository;
import com.ssafy.backend.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AUTH-05 내 프로필 조회, AUTH-06 내 프로필 수정, AUTH-07 비밀번호 변경, AUTH-08 회원 탈퇴, AUTH-10 사용자 검색 로직. 이후 AUTH-11도 여기에 추가될 예정.
 */
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int SEARCH_RESULT_LIMIT = 7;

    private static final long MAX_PROFILE_IMAGE_BYTES = 5L * 1024 * 1024; // 5MB
    private static final java.util.Set<String> ALLOWED_IMAGE_EXTENSIONS = java.util.Set.of("jpg", "jpeg", "png");

    private final UserRepository userRepository;
    private final UserProfileMapper userProfileMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;
    private final RefreshTokenService refreshTokenService;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional(readOnly = true)
    public ResponseMyProfileDto findMyProfile(Long userId) {
        // JWT principal이 가리키는 userId는 로그인 시 실제 DB에서 조회해 발급된 값이라 이론상 항상 존재하지만,
        // 탈퇴 등으로 이후 유효하지 않게 될 가능성을 방어적으로 처리한다.
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        return userProfileMapper.toMyProfileResponse(user);
    }

    @Override
    @Transactional
    public ResponseMyProfileDto modifyMyProfile(Long userId, RequestUpdateProfileDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 로그인 후 Access Token(30분)이 만료되기 전에 탈퇴 처리된 경우를 방어 — 탈퇴 계정은 더 이상 수정할 수 없다.
        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.ALREADY_DELETED_USER);
        }

        // user는 영속 상태 엔티티라 필드 변경만으로 트랜잭션 커밋 시 UPDATE가 나간다(더티 체킹) — save() 호출 불필요.
        user.updateProfile(
                request.nickname(), request.phone(), request.sex(), request.age(),
                request.jobFamily(), request.jobRole(), request.userDescription(), request.userColor()
        );

        return userProfileMapper.toMyProfileResponse(user);
    }

    @Override
    @Transactional
    public ResponseChangePasswordDto changePassword(Long userId, RequestChangePasswordDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 현재 비밀번호 BCrypt 검증
        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 새 비밀번호로 갱신 (더티 체킹 — save() 불필요)
        user.updatePassword(passwordEncoder.encode(request.newPassword()));

        // 새 토큰 발급 및 Redis Refresh Token 교체
        String userIdStr = String.valueOf(userId);
        String newAccessToken = jwtProvider.createAccessToken(userIdStr);
        String newRefreshToken = jwtProvider.createRefreshToken(userIdStr);
        refreshTokenService.save(userIdStr, newRefreshToken, jwtProvider.getRefreshExpirationSeconds());

        return new ResponseChangePasswordDto("Bearer", newAccessToken, newRefreshToken);
    }

    @Override
    @Transactional
    public void withdraw(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        if (user.isDeleted()) {
            throw new CustomException(ErrorCode.ALREADY_DELETED_USER);
        }

        // 영속 엔티티 필드 변경 → 더티 체킹으로 자동 UPDATE, save() 불필요.
        user.withdraw();

        // 탈퇴 후에도 남아있는 Refresh Token으로 AUTH-03 재발급이 이어지는 걸 막는다(AUTH-04 로그아웃과 동일 로직).
        refreshTokenService.delete(String.valueOf(userId));
    }

    @Override
    @Transactional
    public ResponseProfileImageDto changeProfileImage(Long userId, MultipartFile file) {
        if (file.getSize() > MAX_PROFILE_IMAGE_BYTES) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_TOO_LARGE);
        }

        String originalFilename = file.getOriginalFilename();
        String ext = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase()
                : "";
        if (!ALLOWED_IMAGE_EXTENSIONS.contains(ext)) {
            throw new CustomException(ErrorCode.PROFILE_IMAGE_INVALID_TYPE);
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        // 기존 사진 삭제 (없으면 스킵)
        if (user.getProfileImageUrl() != null) {
            fileStorageService.delete(user.getProfileImageUrl());
        }

        // 새 사진 업로드 후 URL 갱신 (더티 체킹 — save() 불필요)
        String newUrl = fileStorageService.upload(file, "profile-images");
        user.updateProfileImage(newUrl);

        return new ResponseProfileImageDto(newUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResponseUserSearchDto> findUserList(Long userId, String query) {
        // query가 비어있으면(공백 포함) 사실상 전체 목록 조회가 되어버리므로, 에러 대신 빈 결과로 처리한다.
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // 검색 목적(멤버 초대 대상 탐색)상 본인은 대상이 될 수 없어 결과에서 제외한다.
        List<User> users = userRepository.searchByNicknameOrEmail(query.trim(), userId, Limit.of(SEARCH_RESULT_LIMIT));
        return users.stream()
                .map(userProfileMapper::toUserSearchResponse)
                .toList();
    }
}