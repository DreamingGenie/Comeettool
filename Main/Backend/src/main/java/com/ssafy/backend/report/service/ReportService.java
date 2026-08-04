package com.ssafy.backend.report.service;

import org.springframework.data.domain.Pageable;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.dto.RequestExportDto;
import com.ssafy.backend.report.dto.ResponseExportDto;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;

public interface ReportService {

    // REPORTS-01: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 회의 전사 목록을 페이지 단위로 조회한다.
    PageResponse<TranscriptSummaryDto> getTranscripts(Long requesterId, Long spaceId, Pageable pageable);

    // REPORTS-02: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 특정 회의의 전사 상세를 조회한다.
    TranscriptDetailDto getTranscript(Long requesterId, Long meetingId);

    // REPORTS-03: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 회의 전사를 md/pdf로 내보낸다(캐시 우선).
    ResponseExportDto exportTranscript(Long requesterId, Long meetingId, RequestExportDto request);

    // REPORTS-04: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 회의록 목록을 페이지 단위로 조회한다.
    PageResponse<MinutesSummaryDto> getMinutesList(Long requesterId, Long spaceId, Pageable pageable);
}
