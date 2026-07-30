package com.ssafy.backend.space.controller;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.space.dto.RequestCreateSpaceDto;
import com.ssafy.backend.space.dto.RequestTransferOwnerDto;
import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseTransferOwnerDto;
import com.ssafy.backend.space.service.SpaceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
    public ResponseEntity<ApiResponse<ResponseCreateSpaceDto>> addSpace(
            @AuthenticationPrincipal String userId,
            @Valid @RequestBody RequestCreateSpaceDto request) {
        ResponseCreateSpaceDto response = spaceService.addSpace(Long.parseLong(userId), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("스페이스가 생성되었습니다.", response));
    }

    // SPACE-02: 참여 중인 스페이스 목록
    @GetMapping
    public ResponseEntity<ApiResponse<List<ResponseSpaceListDto>>> findSpaceList(
            @AuthenticationPrincipal String userId) {
        List<ResponseSpaceListDto> response = spaceService.findSpaceList(Long.parseLong(userId));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // SPACE-05: 스페이스 상세(정보 + 참여자)
    @GetMapping("/{spaceId}")
    public ResponseEntity<ApiResponse<ResponseSpaceDetailDto>> findSpaceDetails(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        ResponseSpaceDetailDto response = spaceService.findSpaceDetails(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // SPACE-07: 스페이스 나가기(요청자 본인). Owner는 소유권 위임 후에만 가능(정책 SP-1).
    @DeleteMapping("/{spaceId}/members/me")
    public ResponseEntity<ApiResponse<Void>> removeMyMembership(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        spaceService.removeMyMembership(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success("스페이스에서 나갔습니다.", null));
    }

    // SPACE-11: 스페이스 삭제(Owner 전용). 하위 데이터까지 전파 soft delete(정책 SP-2).
    @DeleteMapping("/{spaceId}")
    public ResponseEntity<ApiResponse<Void>> removeSpace(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId) {
        spaceService.removeSpace(Long.parseLong(userId), spaceId);
        return ResponseEntity.ok(ApiResponse.success("스페이스가 삭제되었습니다.", null));
    }

    // SPACE-101: 소유권 위임(Owner 전용). 대상 멤버를 새 Owner로 승격하고 기존 Owner는 MEMBER로 강등한다.
    @PatchMapping("/{spaceId}/owner")
    public ResponseEntity<ApiResponse<ResponseTransferOwnerDto>> transferOwner(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @Valid @RequestBody RequestTransferOwnerDto request) {
        ResponseTransferOwnerDto response =
                spaceService.transferOwner(Long.parseLong(userId), spaceId, request);
        return ResponseEntity.ok(ApiResponse.success("소유권이 위임되었습니다.", response));
    }
}
