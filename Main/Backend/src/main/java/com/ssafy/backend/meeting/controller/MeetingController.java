package com.ssafy.backend.meeting.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseMeetingParticipantDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.service.MeetingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * 회의 기능 API.
 */
@RestController
@RequestMapping("/api/v1/meetings")
@RequiredArgsConstructor
public class MeetingController {

    private final MeetingService meetingService;

    /**
     * MEET-06: 현재 호스트가 회의 참여자에게 호스트 권한을 양도한다.
     */
    @PostMapping("/{meetingId}/grant")
    public ResponseEntity<ApiResponse<ResponseTransferHostDto>> transferHost(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId,
            @Valid @RequestBody RequestTransferHostDto request
    ) {
        ResponseTransferHostDto response =
                meetingService.transferHost(Long.parseLong(userId), meetingId, request);
        return ResponseEntity.ok(ApiResponse.success("호스트 권한이 양도되었습니다.", response));
    }

    /**
     * MEET-07: 현재 회의에 입장 중인 참여자의 기본 정보를 조회한다.
     * 카메라·마이크 상태는 추후 LiveKit에서 실시간으로 결합한다.
     */
    @GetMapping("/{meetingId}/participants")
    public ResponseEntity<ApiResponse<List<ResponseMeetingParticipantDto>>> getParticipants(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId
    ) {
        List<ResponseMeetingParticipantDto> response =
                meetingService.getParticipants(Long.parseLong(userId), meetingId);
        return ResponseEntity.ok(ApiResponse.success("회의 참여자 목록을 조회했습니다.", response));
    }
}
