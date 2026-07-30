package com.ssafy.backend.meeting.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
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
}
