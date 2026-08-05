package com.ssafy.backend.report.service;

import org.springframework.data.domain.Pageable;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.report.dto.FacilitatorReportDetailDto;
import com.ssafy.backend.report.dto.FacilitatorReportSummaryDto;
import com.ssafy.backend.report.dto.MinutesDetailDto;
import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.dto.RequestExportDto;
import com.ssafy.backend.report.dto.RequestUpdateMinutesDto;
import com.ssafy.backend.report.dto.ResponseConfirmMinutesDto;
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

    // REPORTS-05: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 특정 회의의 회의록 상세를 조회한다.
    MinutesDetailDto getMinutes(Long requesterId, Long meetingId);

    // REPORTS-06: 스페이스 OWNER/MEMBER(GUEST 제외)가 특정 회의의 회의록을 부분 수정한다. 확정된 회의록은 수정할 수 없다.
    MinutesDetailDto updateMinutes(Long requesterId, Long meetingId, RequestUpdateMinutesDto request);

    // REPORTS-07: 스페이스 OWNER/MEMBER(GUEST 제외)가 특정 회의의 회의록을 확정한다(멱등 — 이미 확정이면 그대로 반환).
    ResponseConfirmMinutesDto confirmMinutes(Long requesterId, Long meetingId);

    // REPORTS-08: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 확정된 회의록을 md/pdf로 내보낸다(캐시 우선).
    ResponseExportDto exportMinutes(Long requesterId, Long meetingId, RequestExportDto request);

    // REPORTS-09: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 퍼실리테이터 리포트 목록을 페이지 단위로 조회한다.
    PageResponse<FacilitatorReportSummaryDto> getFacilitatorReports(Long requesterId, Long spaceId, Pageable pageable);

    // REPORTS-10: 스페이스 멤버(OWNER/MEMBER/GUEST 전부)가 특정 회의의 퍼실리테이터 리포트 상세를 조회한다.
    FacilitatorReportDetailDto getFacilitatorReport(Long requesterId, Long meetingId);
}
