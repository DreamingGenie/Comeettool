package com.ssafy.backend.report.service.impl;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

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
import com.ssafy.backend.report.entity.MeetingMinutes;
import com.ssafy.backend.report.export.MarkdownToPdfConverter;
import com.ssafy.backend.report.export.TranscriptMarkdownRenderer;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.report.repository.MeetingMinutesRepository;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ReportServiceImpl 단위 테스트. Repository는 전부 Mock — 비즈니스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportServiceImpl 단위 테스트")
class ReportServiceImplTest {

    private static final Long OWNER_ID = 1L;
    private static final Long SPACE_ID = 10L;
    private static final Long MEETING_ID = 34L;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private MeetingRoomRepository meetingRoomRepository;

    @Mock
    private AudioTranscriptionRepository audioTranscriptionRepository;

    @Mock
    private MeetingMinutesRepository meetingMinutesRepository;

    @Mock
    private TranscriptMarkdownRenderer transcriptMarkdownRenderer;

    @Mock
    private MarkdownToPdfConverter markdownToPdfConverter;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private ReportServiceImpl reportService;

    private Team activeTeam(Long spaceId, Long ownerId) {
        Team team = Team.builder().name("팀A").description("설명").ownerId(ownerId).color("#123456").build();
        ReflectionTestUtils.setField(team, "id", spaceId);
        return team;
    }

    private MeetingRoom meetingRoomOf(Long meetingId, Long teamId) {
        MeetingRoom meetingRoom = MeetingRoom.builder().teamId(teamId).hostId(OWNER_ID).name("스프린트 회의").build();
        ReflectionTestUtils.setField(meetingRoom, "id", meetingId);
        return meetingRoom;
    }

    // AudioTranscription은 순수 조회 전용 엔티티라 빌더/공개 생성자를 두지 않는다 —
    // 테스트에서는 protected 기본 생성자를 리플렉션으로 열어 ReflectionTestUtils로 필드를 채운다.
    private AudioTranscription transcriptionOf(Long meetingId, String transcript, OffsetDateTime createdAt) {
        try {
            Constructor<AudioTranscription> constructor = AudioTranscription.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            AudioTranscription entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "meetingId", meetingId);
            ReflectionTestUtils.setField(entity, "transcript", transcript);
            ReflectionTestUtils.setField(entity, "createdAt", createdAt);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // MeetingMinutes도 AudioTranscription과 동일하게 순수 조회 전용(@Immutable, 빌더 없음) 엔티티라
    // protected 기본 생성자를 리플렉션으로 열어 ReflectionTestUtils로 필드를 채운다.
    private MeetingMinutes minutesOf(Long meetingId, String title, Boolean isConfirmed, OffsetDateTime createdAt) {
        try {
            Constructor<MeetingMinutes> constructor = MeetingMinutes.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MeetingMinutes entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "meetingId", meetingId);
            ReflectionTestUtils.setField(entity, "title", title);
            ReflectionTestUtils.setField(entity, "isConfirmed", isConfirmed);
            ReflectionTestUtils.setField(entity, "createdAt", createdAt);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    @Nested
    @DisplayName("REPORTS-01 전사 목록 조회")
    class GetTranscripts {

        @Test
        @DisplayName("정상 조회 시 Page 결과가 PageResponse 필드로 정확히 매핑된다")
        void getTranscripts_returnsMappedPageResponse() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<TranscriptSummaryDto> content = List.of(
                    new TranscriptSummaryDto(34L, "8월 4주차 스프린트 회의", OffsetDateTime.parse("2026-08-04T05:00:00Z")),
                    new TranscriptSummaryDto(33L, "7월 회고", OffsetDateTime.parse("2026-07-28T05:00:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> page = new PageImpl<>(content, pageable, 23);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).meetingId()).isEqualTo(34L);
            assertThat(result.content().get(0).meetingRoomName()).isEqualTo("8월 4주차 스프린트 회의");
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(23);
            assertThat(result.totalPages()).isEqualTo(3);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("결과가 없으면 빈 content를 반환한다(예외 아님)")
        void getTranscripts_returnsEmptyContentWhenNoneExist() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(emptyPage);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.totalPages()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버·전사 조회를 시도하지 않는다")
        void getTranscripts_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscripts(OWNER_ID, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 전사 조회를 시도하지 않는다")
        void getTranscripts_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getTranscripts(nonMemberId, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getTranscripts_allowsNonOwnerMemberRequester() {
            // 요청자(guestUserId)는 team.ownerId(OWNER_ID)가 아니지만, 스페이스 멤버이기만 하면 조회를 허용한다.
            Long guestUserId = 55L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<TranscriptSummaryDto> page = new PageImpl<>(List.of(), pageable, 0);
            given(audioTranscriptionRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<TranscriptSummaryDto> result = reportService.getTranscripts(guestUserId, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("page=1, size=5 요청 시 Repository에 동일한 페이지 파라미터가 그대로 전달된다")
        void getTranscripts_passesPageableToRepository() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable requested = PageRequest.of(1, 5);
            Page<TranscriptSummaryDto> page = new PageImpl<>(List.of(), requested, 0);
            given(audioTranscriptionRepository.findAllByTeamId(eq(SPACE_ID), any(Pageable.class))).willReturn(page);

            reportService.getTranscripts(OWNER_ID, SPACE_ID, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(audioTranscriptionRepository).findAllByTeamId(eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("REPORTS-02 전사 상세 조회")
    class GetTranscript {

        private static final String TRANSCRIPT_JSON =
                "[{\"speaker\":\"ssong123\",\"start\":12.5,\"end\":15.8,\"text\":\"그럼 다음 안건으로 넘어가겠습니다\"}]";

        @Test
        @DisplayName("정상 조회 시 transcript가 JSON 배열 원문 그대로 응답 DTO에 매핑된다")
        void getTranscript_returnsMappedDetail() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:00:00Z");
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            TranscriptDetailDto result = reportService.getTranscript(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.transcript()).isEqualTo(TRANSCRIPT_JSON);
            assertThat(result.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·전사 조회를 시도하지 않는다")
        void getTranscript_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscript(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 전사 조회를 시도하지 않는다")
        void getTranscript_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getTranscript(nonMemberId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getTranscript_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:00:00Z");
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            TranscriptDetailDto result = reportService.getTranscript(guestUserId, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
        }

        @Test
        @DisplayName("회의는 있지만 전사가 없으면(처리 중) TRANSCRIPT_NOT_FOUND 예외가 발생한다")
        void getTranscript_throwsWhenTranscriptNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getTranscript(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("REPORTS-03 전사 내보내기")
    class ExportTranscript {

        private static final String TRANSCRIPT_JSON =
                "[{\"speaker\":\"ssong123\",\"start\":12.5,\"end\":15.8,\"text\":\"그럼 다음 안건으로 넘어가겠습니다\"}]";
        private static final String RENDERED_MARKDOWN =
                "# 회의 전사 (Meeting #34)\n\n- [00:12] ssong123: 그럼 다음 안건으로 넘어가겠습니다\n";

        @Test
        @DisplayName("md 캐시가 없으면 렌더링·업로드 후 mdUrl을 갱신하고 새 URL을 반환한다")
        void exportTranscript_rendersAndUploadsWhenMdCacheMissing() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, OffsetDateTime.now());
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));
            given(transcriptMarkdownRenderer.render(MEETING_ID, TRANSCRIPT_JSON)).willReturn(RENDERED_MARKDOWN);
            given(fileStorageService.upload(any(byte[].class), eq("text/markdown"), eq("transcripts")))
                    .willReturn("https://bucket.s3.region.amazonaws.com/transcripts/34.md");

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("md"));

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.format()).isEqualTo("md");
            assertThat(result.url()).isEqualTo("https://bucket.s3.region.amazonaws.com/transcripts/34.md");
            verify(fileStorageService).upload(any(byte[].class), eq("text/markdown"), eq("transcripts"));
            verify(audioTranscriptionRepository)
                    .updateMdUrl(MEETING_ID, "https://bucket.s3.region.amazonaws.com/transcripts/34.md");
            verify(markdownToPdfConverter, never()).convert(any());
            verify(audioTranscriptionRepository, never()).updatePdfUrl(any(), any());
        }

        @Test
        @DisplayName("pdf 캐시가 없으면 렌더링·PDF 변환·업로드 후 pdfUrl을 갱신하고 새 URL을 반환한다")
        void exportTranscript_rendersAndUploadsWhenPdfCacheMissing() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, OffsetDateTime.now());
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));
            given(transcriptMarkdownRenderer.render(MEETING_ID, TRANSCRIPT_JSON)).willReturn(RENDERED_MARKDOWN);
            byte[] pdfBytes = {1, 2, 3};
            given(markdownToPdfConverter.convert(RENDERED_MARKDOWN)).willReturn(pdfBytes);
            given(fileStorageService.upload(pdfBytes, "application/pdf", "transcripts"))
                    .willReturn("https://bucket.s3.region.amazonaws.com/transcripts/34.pdf");

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("pdf"));

            assertThat(result.format()).isEqualTo("pdf");
            assertThat(result.url()).isEqualTo("https://bucket.s3.region.amazonaws.com/transcripts/34.pdf");
            verify(fileStorageService).upload(pdfBytes, "application/pdf", "transcripts");
            verify(audioTranscriptionRepository)
                    .updatePdfUrl(MEETING_ID, "https://bucket.s3.region.amazonaws.com/transcripts/34.pdf");
            verify(audioTranscriptionRepository, never()).updateMdUrl(any(), any());
        }

        @Test
        @DisplayName("mdUrl 캐시가 있으면 렌더링·업로드를 스킵하고 기존 URL을 그대로 반환한다")
        void exportTranscript_returnsCachedMdUrlWithoutUploading() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, OffsetDateTime.now());
            ReflectionTestUtils.setField(transcription, "mdUrl", "https://bucket.s3.region.amazonaws.com/cached.md");
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("md"));

            assertThat(result.url()).isEqualTo("https://bucket.s3.region.amazonaws.com/cached.md");
            verifyNoInteractions(transcriptMarkdownRenderer);
            verifyNoInteractions(markdownToPdfConverter);
            verify(fileStorageService, never()).upload(any(byte[].class), any(), any());
            verify(audioTranscriptionRepository, never()).updateMdUrl(any(), any());
        }

        @Test
        @DisplayName("pdfUrl 캐시가 있으면 렌더링·업로드를 스킵하고 기존 URL을 그대로 반환한다")
        void exportTranscript_returnsCachedPdfUrlWithoutUploading() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            AudioTranscription transcription = transcriptionOf(MEETING_ID, TRANSCRIPT_JSON, OffsetDateTime.now());
            ReflectionTestUtils.setField(
                    transcription, "pdfUrl", "https://bucket.s3.region.amazonaws.com/cached.pdf");
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.of(transcription));

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("pdf"));

            assertThat(result.url()).isEqualTo("https://bucket.s3.region.amazonaws.com/cached.pdf");
            verifyNoInteractions(transcriptMarkdownRenderer);
            verifyNoInteractions(markdownToPdfConverter);
            verify(fileStorageService, never()).upload(any(byte[].class), any(), any());
            verify(audioTranscriptionRepository, never()).updatePdfUrl(any(), any());
        }

        @Test
        @DisplayName("format이 md/pdf가 아니면 VALIDATION_FAILED 예외가 발생하고 전사 조회를 시도하지 않는다")
        void exportTranscript_throwsWhenFormatIsInvalid() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            assertThatThrownBy(() ->
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("docx")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·전사 조회를 시도하지 않는다")
        void exportTranscript_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("md")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생한다")
        void exportTranscript_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() ->
                    reportService.exportTranscript(nonMemberId, MEETING_ID, new RequestExportDto("md")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(audioTranscriptionRepository);
        }

        @Test
        @DisplayName("회의는 있지만 전사가 없으면 TRANSCRIPT_NOT_FOUND 예외가 발생한다")
        void exportTranscript_throwsWhenTranscriptNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(audioTranscriptionRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() ->
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("md")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.TRANSCRIPT_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("REPORTS-04 회의록 목록 조회")
    class GetMinutesList {

        @Test
        @DisplayName("정상 조회 시 Page 결과가 PageResponse 필드로 정확히 매핑된다")
        void getMinutesList_returnsMappedPageResponse() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<MinutesSummaryDto> content = List.of(
                    new MinutesSummaryDto(
                            34L, "8월 4주차 스프린트 회의", "스프린트 리뷰 회의록", false,
                            OffsetDateTime.parse("2026-08-04T05:10:00Z")),
                    new MinutesSummaryDto(
                            33L, "7월 회고", "7월 회고 회의록", true,
                            OffsetDateTime.parse("2026-07-28T05:10:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            Page<MinutesSummaryDto> page = new PageImpl<>(content, pageable, 12);
            given(meetingMinutesRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<MinutesSummaryDto> result = reportService.getMinutesList(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).meetingId()).isEqualTo(34L);
            assertThat(result.content().get(0).meetingRoomName()).isEqualTo("8월 4주차 스프린트 회의");
            assertThat(result.content().get(0).title()).isEqualTo("스프린트 리뷰 회의록");
            assertThat(result.content().get(0).isConfirmed()).isFalse();
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(12);
            assertThat(result.totalPages()).isEqualTo(2);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("결과가 없으면 빈 content를 반환한다(예외 아님)")
        void getMinutesList_returnsEmptyContentWhenNoneExist() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<MinutesSummaryDto> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(meetingMinutesRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(emptyPage);

            PageResponse<MinutesSummaryDto> result = reportService.getMinutesList(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.totalPages()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버·회의록 조회를 시도하지 않는다")
        void getMinutesList_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getMinutesList(OWNER_ID, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 회의록 조회를 시도하지 않는다")
        void getMinutesList_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getMinutesList(nonMemberId, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getMinutesList_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<MinutesSummaryDto> page = new PageImpl<>(List.of(), pageable, 0);
            given(meetingMinutesRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<MinutesSummaryDto> result = reportService.getMinutesList(guestUserId, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("isConfirmed=true/false가 필터 없이 목록에 섞여서 그대로 반환된다")
        void getMinutesList_returnsBothConfirmedAndUnconfirmedWithoutFiltering() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<MinutesSummaryDto> content = List.of(
                    new MinutesSummaryDto(34L, "회의A", "회의록A", true, OffsetDateTime.parse("2026-08-04T05:10:00Z")),
                    new MinutesSummaryDto(33L, "회의B", "회의록B", false, OffsetDateTime.parse("2026-07-28T05:10:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            Page<MinutesSummaryDto> page = new PageImpl<>(content, pageable, 2);
            given(meetingMinutesRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<MinutesSummaryDto> result = reportService.getMinutesList(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content())
                    .extracting(MinutesSummaryDto::isConfirmed)
                    .containsExactly(true, false);
        }

        @Test
        @DisplayName("page=1, size=5 요청 시 Repository에 동일한 페이지 파라미터가 그대로 전달된다")
        void getMinutesList_passesPageableToRepository() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable requested = PageRequest.of(1, 5);
            Page<MinutesSummaryDto> page = new PageImpl<>(List.of(), requested, 0);
            given(meetingMinutesRepository.findAllByTeamId(eq(SPACE_ID), any(Pageable.class))).willReturn(page);

            reportService.getMinutesList(OWNER_ID, SPACE_ID, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(meetingMinutesRepository).findAllByTeamId(eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }
    }
}
