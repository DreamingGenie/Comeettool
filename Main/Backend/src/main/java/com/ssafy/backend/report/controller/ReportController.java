package com.ssafy.backend.report.controller;

import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.response.ApiResponse;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.service.ReportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    // REPORTS-01: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 회의 전사 목록을 페이지 단위로 조회한다.
    @GetMapping("/api/v1/spaces/{spaceId}/reports/transcripts")
    public ResponseEntity<ApiResponse<PageResponse<TranscriptSummaryDto>>> getTranscripts(
            @AuthenticationPrincipal String userId,
            @PathVariable Long spaceId,
            @PageableDefault(page = 0, size = 10) Pageable pageable) {
        PageResponse<TranscriptSummaryDto> response =
                reportService.getTranscripts(Long.parseLong(userId), spaceId, pageable);
        return ResponseEntity.ok(ApiResponse.success("전사 목록 조회 성공", response));
    }

    // REPORTS-02: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 특정 회의의 전사 상세를 조회한다.
    @GetMapping("/api/v1/meetings/{meetingId}/reports/transcript")
    public ResponseEntity<ApiResponse<TranscriptDetailDto>> getTranscript(
            @AuthenticationPrincipal String userId,
            @PathVariable Long meetingId) {
        TranscriptDetailDto response = reportService.getTranscript(Long.parseLong(userId), meetingId);
        return ResponseEntity.ok(ApiResponse.success("전사 조회 성공", response));
    }
}
