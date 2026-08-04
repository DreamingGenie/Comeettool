package com.ssafy.backend.report.service.impl;

import java.nio.charset.StandardCharsets;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.FileStorageService;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.dto.RequestExportDto;
import com.ssafy.backend.report.dto.ResponseExportDto;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.entity.AudioTranscription;
import com.ssafy.backend.report.export.MarkdownToPdfConverter;
import com.ssafy.backend.report.export.TranscriptMarkdownRenderer;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.report.repository.MeetingMinutesRepository;
import com.ssafy.backend.report.service.ReportService;
import com.ssafy.backend.space.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private static final String TRANSCRIPT_UPLOAD_DIRECTORY = "transcripts";

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;
    private final MeetingRoomRepository meetingRoomRepository;
    private final AudioTranscriptionRepository audioTranscriptionRepository;
    private final MeetingMinutesRepository meetingMinutesRepository;
    private final TranscriptMarkdownRenderer transcriptMarkdownRenderer;
    private final MarkdownToPdfConverter markdownToPdfConverter;
    private final FileStorageService fileStorageService;

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

        String url;
        if (isMd) {
            byte[] content = markdown.getBytes(StandardCharsets.UTF_8);
            url = fileStorageService.upload(content, "text/markdown", TRANSCRIPT_UPLOAD_DIRECTORY);
            audioTranscriptionRepository.updateMdUrl(meetingId, url);
        } else {
            byte[] content = markdownToPdfConverter.convert(markdown);
            url = fileStorageService.upload(content, "application/pdf", TRANSCRIPT_UPLOAD_DIRECTORY);
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
}
