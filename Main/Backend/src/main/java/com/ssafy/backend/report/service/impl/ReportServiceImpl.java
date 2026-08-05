package com.ssafy.backend.report.service.impl;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.ObjectStorageService;
import com.ssafy.backend.global.storage.StorageObjectKey;
import com.ssafy.backend.global.storage.StorageUploadRequest;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.report.dto.FacilitatorReportSummaryDto;
import com.ssafy.backend.report.dto.MinutesDetailDto;
import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.dto.RequestExportDto;
import com.ssafy.backend.report.dto.RequestUpdateMinutesDto;
import com.ssafy.backend.report.dto.ResponseConfirmMinutesDto;
import com.ssafy.backend.report.dto.ResponseExportDto;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.entity.AudioTranscription;
import com.ssafy.backend.report.entity.MeetingMinutes;
import com.ssafy.backend.report.export.AiResultKeys;
import com.ssafy.backend.report.export.MarkdownToPdfConverter;
import com.ssafy.backend.report.export.TranscriptMarkdownRenderer;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.report.repository.FacilitatorReportRepository;
import com.ssafy.backend.report.repository.MeetingMinutesRepository;
import com.ssafy.backend.report.service.ReportService;
import com.ssafy.backend.space.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final AudioTranscriptionRepository audioTranscriptionRepository;
    private final MeetingMinutesRepository meetingMinutesRepository;
    private final FacilitatorReportRepository facilitatorReportRepository;
    private final TranscriptMarkdownRenderer transcriptMarkdownRenderer;
    private final MarkdownToPdfConverter markdownToPdfConverter;
    private final ObjectStorageService objectStorageService;

    @Value("${file.base-url}")
    private String publicBaseUrl;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<TranscriptSummaryDto> getTranscripts(Long requesterId, Long spaceId, Pageable pageable) {
        teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — MEMBER-15와 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 쿼리 자체가 createdAt desc로 고정 정렬돼 있어 클라이언트가 넘긴 sort는 반영 대상이 아니다.
        // 그대로 흘려보내면 select 절에 없는 프로퍼티로 정렬 시도 시 500(InvalidDataAccessApiUsageException)이 나므로 페이지 정보만 취한다.
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<TranscriptSummaryDto> page = audioTranscriptionRepository.findAllByTeamId(spaceId, pageOnly);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public TranscriptDetailDto getTranscript(Long requesterId, Long meetingId) {
        // 종료(soft delete)된 회의도 전사 조회 대상이라 활성 여부는 걸지 않는다 — 전사는 회의 종료 후 생성된다.
        MeetingRoom meetingRoom = meetingRoomRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — REPORTS-01과 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(meetingRoom.getTeamId(), requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        AudioTranscription transcription = audioTranscriptionRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSCRIPT_NOT_FOUND));

        return new TranscriptDetailDto(
                transcription.getMeetingId(), transcription.getTranscript(), transcription.getCreatedAt());
    }

    @Override
    @Transactional
    public ResponseExportDto exportTranscript(Long requesterId, Long meetingId, RequestExportDto request) {
        MeetingRoom meetingRoom = meetingRoomRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — REPORTS-01/02와 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(meetingRoom.getTeamId(), requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        String format = request.format();
        if (!"md".equals(format) && !"pdf".equals(format)) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }

        AudioTranscription transcription = audioTranscriptionRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.TRANSCRIPT_NOT_FOUND));

        boolean isMd = "md".equals(format);
        String cachedUrl = isMd ? transcription.getMdUrl() : transcription.getPdfUrl();
        if (cachedUrl != null) {
            return new ResponseExportDto(meetingId, format, cachedUrl);
        }

        String markdown = transcriptMarkdownRenderer.render(meetingId, transcription.getTranscript());

        byte[] content;
        String contentType;
        StorageObjectKey objectKey;
        if (isMd) {
            content = markdown.getBytes(StandardCharsets.UTF_8);
            contentType = "text/markdown";
            objectKey = AiResultKeys.of(meetingId, "transcript", "md");
        } else {
            content = markdownToPdfConverter.convert(markdown);
            contentType = "application/pdf";
            objectKey = AiResultKeys.of(meetingId, "transcript", "pdf");
        }

        StorageUploadRequest uploadRequest = new StorageUploadRequest(objectKey, content.length, contentType, null);
        objectStorageService.upload(uploadRequest, new ByteArrayInputStream(content));
        String url = publicBaseUrl + "/files/" + objectKey.value();

        if (isMd) {
            audioTranscriptionRepository.updateMdUrl(meetingId, url);
        } else {
            audioTranscriptionRepository.updatePdfUrl(meetingId, url);
        }

        return new ResponseExportDto(meetingId, format, url);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<MinutesSummaryDto> getMinutesList(Long requesterId, Long spaceId, Pageable pageable) {
        teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — REPORTS-01과 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 쿼리 자체가 createdAt desc로 고정 정렬돼 있어 클라이언트가 넘긴 sort는 반영 대상이 아니다.
        // 그대로 흘려보내면 select 절에 없는 프로퍼티로 정렬 시도 시 500(InvalidDataAccessApiUsageException)이 나므로 페이지 정보만 취한다.
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<MinutesSummaryDto> page = meetingMinutesRepository.findAllByTeamId(spaceId, pageOnly);
        return PageResponse.from(page);
    }

    @Override
    @Transactional(readOnly = true)
    public MinutesDetailDto getMinutes(Long requesterId, Long meetingId) {
        // 종료(soft delete)된 회의도 회의록 조회 대상이라 활성 여부는 걸지 않는다 — REPORTS-02와 동일한 이유.
        MeetingRoom meetingRoom = meetingRoomRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — REPORTS-02와 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(meetingRoom.getTeamId(), requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        MeetingMinutes minutes = meetingMinutesRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MINUTES_NOT_FOUND));

        return new MinutesDetailDto(
                minutes.getMeetingId(),
                minutes.getTitle(),
                minutes.getSummary(),
                minutes.getTopics(),
                minutes.getDecisions(),
                minutes.getActionItems(),
                minutes.getOpenIssues(),
                minutes.getIsConfirmed(),
                minutes.getConfirmedAt(),
                minutes.getCreatedAt());
    }

    @Override
    @Transactional
    public MinutesDetailDto updateMinutes(Long requesterId, Long meetingId, RequestUpdateMinutesDto request) {
        MeetingRoom meetingRoom = meetingRoomRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 수정은 조회(REPORTS-01/02/04/05)와 달리 OWNER/MEMBER만 허용 — GUEST이거나 비멤버면 거부.
        Member member = memberRepository.findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.MINUTES_EDIT_DENIED));
        if (member.getAuthority() == MemberAuthority.GUEST) {
            throw new CustomException(ErrorCode.MINUTES_EDIT_DENIED);
        }

        MeetingMinutes minutes = meetingMinutesRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MINUTES_NOT_FOUND));

        // 확정된 회의록은 필드 값과 무관하게 즉시 거부 — 아래 필드 검증/반영보다 먼저 걸러야 한다.
        if (Boolean.TRUE.equals(minutes.getIsConfirmed())) {
            throw new CustomException(ErrorCode.MINUTES_ALREADY_CONFIRMED);
        }

        // title/summary는 값이 오면(null이 아니면) 빈 문자열을 금지 — 실제 반영 전에 먼저 걸러 부분 실패를 방지한다.
        if (request.title() != null && request.title().isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }
        if (request.summary() != null && request.summary().isBlank()) {
            throw new CustomException(ErrorCode.VALIDATION_FAILED);
        }

        String title = minutes.getTitle();
        if (request.title() != null) {
            title = request.title();
            meetingMinutesRepository.updateTitle(meetingId, title);
        }

        String summary = minutes.getSummary();
        if (request.summary() != null) {
            summary = request.summary();
            meetingMinutesRepository.updateSummary(meetingId, summary);
        }

        // topics/decisions/actionItems/openIssues: 배열 전체 교체. 빈 배열([])도 유효한 값(전체 삭제)이라
        // JsonNode가 JSON null이 아닌 이상(필드 미포함/명시적 null과 구분) 그대로 반영한다.
        String topics = minutes.getTopics();
        if (request.topics() != null && !request.topics().isNull()) {
            topics = request.topics().toString();
            meetingMinutesRepository.updateTopics(meetingId, topics);
        }

        String decisions = minutes.getDecisions();
        if (request.decisions() != null && !request.decisions().isNull()) {
            decisions = request.decisions().toString();
            meetingMinutesRepository.updateDecisions(meetingId, decisions);
        }

        String actionItems = minutes.getActionItems();
        if (request.actionItems() != null && !request.actionItems().isNull()) {
            actionItems = request.actionItems().toString();
            meetingMinutesRepository.updateActionItems(meetingId, actionItems);
        }

        String openIssues = minutes.getOpenIssues();
        if (request.openIssues() != null && !request.openIssues().isNull()) {
            openIssues = request.openIssues().toString();
            meetingMinutesRepository.updateOpenIssues(meetingId, openIssues);
        }

        // MeetingMinutes는 @Immutable이라 위 @Modifying UPDATE가 영속성 컨텍스트에 반영되지 않는다 —
        // 같은 트랜잭션에서 다시 조회해도 1차 캐시의 갱신 전 인스턴스가 그대로 반환되므로, 리로드 대신
        // 이미 로드해 둔 minutes와 위에서 반영한 값으로 응답을 직접 조립한다(REPORTS-03 exportTranscript와 동일 방식).
        return new MinutesDetailDto(
                meetingId, title, summary, topics, decisions, actionItems, openIssues,
                minutes.getIsConfirmed(), minutes.getConfirmedAt(), minutes.getCreatedAt());
    }

    @Override
    @Transactional
    public ResponseConfirmMinutesDto confirmMinutes(Long requesterId, Long meetingId) {
        MeetingRoom meetingRoom = meetingRoomRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEETING_NOT_FOUND));

        // 확정도 수정(REPORTS-06)과 동일하게 OWNER/MEMBER만 허용 — GUEST이거나 비멤버면 거부.
        Member member = memberRepository.findByTeamIdAndUserId(meetingRoom.getTeamId(), requesterId)
                .orElseThrow(() -> new CustomException(ErrorCode.MINUTES_EDIT_DENIED));
        if (member.getAuthority() == MemberAuthority.GUEST) {
            throw new CustomException(ErrorCode.MINUTES_EDIT_DENIED);
        }

        MeetingMinutes minutes = meetingMinutesRepository.findById(meetingId)
                .orElseThrow(() -> new CustomException(ErrorCode.MINUTES_NOT_FOUND));

        // 멱등 처리: 이미 확정된 회의록이면 쓰기 없이 기존 confirmedAt 그대로 응답한다(MEMBER-06/13과 동일한 무변경 컨벤션).
        if (Boolean.TRUE.equals(minutes.getIsConfirmed())) {
            return new ResponseConfirmMinutesDto(meetingId, true, minutes.getConfirmedAt());
        }

        // UPDATE 파라미터와 응답에 같은 값을 재사용 — MeetingMinutes가 @Immutable이라 재조회해도
        // 1차 캐시의 갱신 전 인스턴스가 나오므로(REPORTS-06과 동일 이유), CURRENT_TIMESTAMP 대신
        // 서비스에서 시각을 만들어 UPDATE와 응답 양쪽에 그대로 써서 값을 일치시킨다.
        OffsetDateTime confirmedAt = OffsetDateTime.now(ZoneOffset.UTC);
        meetingMinutesRepository.confirm(meetingId, confirmedAt);

        return new ResponseConfirmMinutesDto(meetingId, true, confirmedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FacilitatorReportSummaryDto> getFacilitatorReports(
            Long requesterId, Long spaceId, Pageable pageable) {
        teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 조회는 OWNER 제한 없이 스페이스 멤버 전체(OWNER/MEMBER/GUEST)에게 허용 — REPORTS-01/04와 동일한 인가 검사.
        if (!memberRepository.existsByTeamIdAndUserId(spaceId, requesterId)) {
            throw new CustomException(ErrorCode.SPACE_ACCESS_DENIED);
        }

        // 쿼리 자체가 createdAt desc로 고정 정렬돼 있어 클라이언트가 넘긴 sort는 반영 대상이 아니다.
        // 그대로 흘려보내면 select 절에 없는 프로퍼티로 정렬 시도 시 500(InvalidDataAccessApiUsageException)이 나므로 페이지 정보만 취한다.
        Pageable pageOnly = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<FacilitatorReportSummaryDto> page = facilitatorReportRepository.findAllByTeamId(spaceId, pageOnly);
        return PageResponse.from(page);
    }
}
