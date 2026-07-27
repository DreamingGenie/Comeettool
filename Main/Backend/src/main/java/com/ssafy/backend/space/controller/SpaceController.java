package com.ssafy.backend.space.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * SPACE 도메인 REST 컨트롤러 (inventory.md §2).
 * 인증 필요(SecurityConfig에서 permitAll 목록에 없음) — principal = userId(String).
 * 요청/응답만 담당하고 비즈니스 로직은 SpaceService에 위임한다.
 */
@RestController
@RequestMapping("/api/v1/spaces")
@RequiredArgsConstructor
public class SpaceController {

    private final SpaceService spaceService;

    // SPACE-01: 스페이스 생성
    @PostMapping
    public ResponseEntity<ApiResponse<ResponseCreateSpaceDto>> createSpace(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RequestCreateSpaceDto request) {
        ResponseCreateSpaceDto response = spaceService.createSpace(Long.parseLong(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("스페이스가 생성되었습니다.", response));
    }

    // SPACE-02: 참여 중인 스페이스 목록
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResponseSpaceListDto>>> findMySpaces(
            @AuthenticationPrincipal String userId) {
        List<ResponseSpaceListDto> response = spaceService.findMySpaces(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // SPACE-05: 스페이스 상세(정보 + 참여자)
    @GetMapping("/{spaceId}")
    public ResponseEntity<ApiResponse<ResponseSpaceDetailDto>> findSpaceDetail(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        ResponseSpaceDetailDto response = spaceService.findSpaceDetail(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
