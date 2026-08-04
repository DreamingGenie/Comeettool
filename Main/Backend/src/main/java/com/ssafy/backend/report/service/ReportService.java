package com.ssafy.backend.report.service;

import org.springframework.data.domain.Pageable;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;

public interface ReportService {

    // REPORTS-01: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 회의 전사 목록을 페이지 단위로 조회한다.
    PageResponse<TranscriptSummaryDto> getTranscripts(Long requesterId, Long spaceId, Pageable pageable);
}
