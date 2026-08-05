package com.ssafy.backend.report.service.impl;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
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

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.storage.ObjectStorageService;
import com.ssafy.backend.global.storage.StorageUploadRequest;
import com.ssafy.backend.meeting.entity.MeetingRoom;
import com.ssafy.backend.meeting.repository.MeetingRoomRepository;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
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
import com.ssafy.backend.report.entity.AudioTranscription;
import com.ssafy.backend.report.entity.FacilitatorReport;
import com.ssafy.backend.report.entity.MeetingMinutes;
import com.ssafy.backend.report.export.MarkdownToPdfConverter;
import com.ssafy.backend.report.export.TranscriptMarkdownRenderer;
import com.ssafy.backend.report.repository.AudioTranscriptionRepository;
import com.ssafy.backend.report.repository.FacilitatorReportRepository;
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
    private static final String BASE_URL = "http://localhost:8080";

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
    private FacilitatorReportRepository facilitatorReportRepository;

    @Mock
    private TranscriptMarkdownRenderer transcriptMarkdownRenderer;

    @Mock
    private MarkdownToPdfConverter markdownToPdfConverter;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private ReportServiceImpl reportService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reportService, "publicBaseUrl", BASE_URL);
    }

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

    // REPORTS-06/07: 회의록 수정·확정 인가 검사(OWNER/MEMBER만 허용, GUEST 거부)용 멤버 픽스처.
    private Member ownerMember() {
        return Member.owner(OWNER_ID, SPACE_ID, "닉네임");
    }

    private Member memberRoleMember(Long userId) {
        return Member.invited(userId, SPACE_ID, "닉네임");
    }

    private Member guestMember(Long userId) {
        Member guest = Member.invited(userId, SPACE_ID, "닉네임");
        ReflectionTestUtils.setField(guest, "authority", MemberAuthority.GUEST);
        return guest;
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
    private MeetingMinutes minutesOf(
            Long meetingId, String title, String summary, String topics, String decisions,
            String actionItems, String openIssues, Boolean isConfirmed, OffsetDateTime confirmedAt,
            OffsetDateTime createdAt) {
        try {
            Constructor<MeetingMinutes> constructor = MeetingMinutes.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            MeetingMinutes entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "meetingId", meetingId);
            ReflectionTestUtils.setField(entity, "title", title);
            ReflectionTestUtils.setField(entity, "summary", summary);
            ReflectionTestUtils.setField(entity, "topics", topics);
            ReflectionTestUtils.setField(entity, "decisions", decisions);
            ReflectionTestUtils.setField(entity, "actionItems", actionItems);
            ReflectionTestUtils.setField(entity, "openIssues", openIssues);
            ReflectionTestUtils.setField(entity, "isConfirmed", isConfirmed);
            ReflectionTestUtils.setField(entity, "confirmedAt", confirmedAt);
            ReflectionTestUtils.setField(entity, "createdAt", createdAt);
            return entity;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    // FacilitatorReport도 MeetingMinutes/AudioTranscription과 동일하게 순수 조회 전용(@Immutable, 빌더 없음)
    // 엔티티라 protected 기본 생성자를 리플렉션으로 열어 ReflectionTestUtils로 필드를 채운다.
    private FacilitatorReport facilitatorReportOf(
            Long meetingId, String title, String meetingType, String overallReview, String participationComment,
            String participationStats, String qualityEvaluation, String strengths, String improvements,
            String decisionProcessChecks, String unresolvedIssuesEvaluation, String nextMeetingSuggestions,
            OffsetDateTime createdAt) {
        try {
            Constructor<FacilitatorReport> constructor = FacilitatorReport.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            FacilitatorReport entity = constructor.newInstance();
            ReflectionTestUtils.setField(entity, "meetingId", meetingId);
            ReflectionTestUtils.setField(entity, "title", title);
            ReflectionTestUtils.setField(entity, "meetingType", meetingType);
            ReflectionTestUtils.setField(entity, "overallReview", overallReview);
            ReflectionTestUtils.setField(entity, "participationComment", participationComment);
            ReflectionTestUtils.setField(entity, "participationStats", participationStats);
            ReflectionTestUtils.setField(entity, "qualityEvaluation", qualityEvaluation);
            ReflectionTestUtils.setField(entity, "strengths", strengths);
            ReflectionTestUtils.setField(entity, "improvements", improvements);
            ReflectionTestUtils.setField(entity, "decisionProcessChecks", decisionProcessChecks);
            ReflectionTestUtils.setField(entity, "unresolvedIssuesEvaluation", unresolvedIssuesEvaluation);
            ReflectionTestUtils.setField(entity, "nextMeetingSuggestions", nextMeetingSuggestions);
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

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("md"));

            String expectedUrl = BASE_URL + "/files/ai-results/34/transcript.md";
            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.format()).isEqualTo("md");
            assertThat(result.url()).isEqualTo(expectedUrl);

            ArgumentCaptor<StorageUploadRequest> requestCaptor = ArgumentCaptor.forClass(StorageUploadRequest.class);
            verify(objectStorageService).upload(requestCaptor.capture(), any(InputStream.class));
            assertThat(requestCaptor.getValue().objectKey().value()).isEqualTo("ai-results/34/transcript.md");
            assertThat(requestCaptor.getValue().contentType()).isEqualTo("text/markdown");
            verify(audioTranscriptionRepository).updateMdUrl(MEETING_ID, expectedUrl);
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

            ResponseExportDto result =
                    reportService.exportTranscript(OWNER_ID, MEETING_ID, new RequestExportDto("pdf"));

            String expectedUrl = BASE_URL + "/files/ai-results/34/transcript.pdf";
            assertThat(result.format()).isEqualTo("pdf");
            assertThat(result.url()).isEqualTo(expectedUrl);

            ArgumentCaptor<StorageUploadRequest> requestCaptor = ArgumentCaptor.forClass(StorageUploadRequest.class);
            verify(objectStorageService).upload(requestCaptor.capture(), any(InputStream.class));
            assertThat(requestCaptor.getValue().objectKey().value()).isEqualTo("ai-results/34/transcript.pdf");
            assertThat(requestCaptor.getValue().contentType()).isEqualTo("application/pdf");
            assertThat(requestCaptor.getValue().contentLength()).isEqualTo(pdfBytes.length);
            verify(audioTranscriptionRepository).updatePdfUrl(MEETING_ID, expectedUrl);
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
            verifyNoInteractions(objectStorageService);
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
            verifyNoInteractions(objectStorageService);
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

    @Nested
    @DisplayName("REPORTS-05 회의록 상세 조회")
    class GetMinutes {

        private static final String TOPICS_JSON = "[{\"topic\":\"REPORTS API 진행 상황 공유\"}]";
        private static final String DECISIONS_JSON =
                "[{\"decision\":\"REPORTS-01~04는 이번 스프린트 내 완료\",\"owner\":\"ssong\"}]";
        private static final String ACTION_ITEMS_JSON =
                "[{\"task\":\"REPORTS-05 이후 스펙 정리\",\"assignee\":\"ssong\",\"dueDate\":\"2026-08-06\"}]";
        private static final String OPEN_ISSUES_JSON = "[{\"issue\":\"STT 파이프라인 세그먼트 병합 로직 미구현\"}]";

        @Test
        @DisplayName("정상 조회 시 topics/decisions/actionItems/openIssues가 JSON 원문 그대로 응답 DTO에 매핑된다")
        void getMinutes_returnsMappedDetail() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:10:00Z");
            MeetingMinutes minutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "이번 스프린트 완료 항목을 리뷰하고 다음 스프린트 우선순위를 논의했습니다.",
                    TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON, OPEN_ISSUES_JSON, false, null, createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            MinutesDetailDto result = reportService.getMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.title()).isEqualTo("스프린트 리뷰 회의록");
            assertThat(result.summary()).isEqualTo("이번 스프린트 완료 항목을 리뷰하고 다음 스프린트 우선순위를 논의했습니다.");
            assertThat(result.topics()).isEqualTo(TOPICS_JSON);
            assertThat(result.decisions()).isEqualTo(DECISIONS_JSON);
            assertThat(result.actionItems()).isEqualTo(ACTION_ITEMS_JSON);
            assertThat(result.openIssues()).isEqualTo(OPEN_ISSUES_JSON);
            assertThat(result.isConfirmed()).isFalse();
            assertThat(result.confirmedAt()).isNull();
            assertThat(result.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·회의록 조회를 시도하지 않는다")
        void getMinutes_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getMinutes(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 회의록 조회를 시도하지 않는다")
        void getMinutes_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getMinutes(nonMemberId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getMinutes_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "요약", TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON,
                    OPEN_ISSUES_JSON, false, null, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            MinutesDetailDto result = reportService.getMinutes(guestUserId, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
        }

        @Test
        @DisplayName("회의는 있지만 회의록이 없으면(아직 생성 안 됨) MINUTES_NOT_FOUND 예외가 발생한다")
        void getMinutes_throwsWhenMinutesNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getMinutes(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_NOT_FOUND);
        }

        @Test
        @DisplayName("확정된(isConfirmed=true) 회의록은 confirmedAt이 정상적으로 매핑된다")
        void getMinutes_mapsConfirmedAtWhenConfirmed() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime confirmedAt = OffsetDateTime.parse("2026-08-05T02:00:00Z");
            MeetingMinutes minutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "요약", TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON,
                    OPEN_ISSUES_JSON, true, confirmedAt, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            MinutesDetailDto result = reportService.getMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.isConfirmed()).isTrue();
            assertThat(result.confirmedAt()).isEqualTo(confirmedAt);
        }

        @Test
        @DisplayName("미확정(isConfirmed=false) 회의록은 confirmedAt이 null로 매핑된다")
        void getMinutes_mapsNullConfirmedAtWhenUnconfirmed() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "요약", TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON,
                    OPEN_ISSUES_JSON, false, null, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            MinutesDetailDto result = reportService.getMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.isConfirmed()).isFalse();
            assertThat(result.confirmedAt()).isNull();
        }
    }

    @Nested
    @DisplayName("REPORTS-06 회의록 부분 수정")
    class UpdateMinutes {

        private static final String TOPICS_JSON = "[{\"topic\":\"REPORTS API 진행 상황 공유\"}]";
        private static final String DECISIONS_JSON =
                "[{\"decision\":\"REPORTS-01~04는 이번 스프린트 내 완료\",\"owner\":\"ssong\"}]";
        private static final String ACTION_ITEMS_JSON =
                "[{\"task\":\"REPORTS-05 이후 스펙 정리\",\"assignee\":\"ssong\",\"dueDate\":\"2026-08-06\"}]";
        private static final String OPEN_ISSUES_JSON = "[{\"issue\":\"STT 파이프라인 세그먼트 병합 로직 미구현\"}]";
        private static final String NEW_TOPICS_JSON = "[{\"topic\":\"새 안건\"}]";

        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private static JsonNode json(String raw) {
            return OBJECT_MAPPER.readTree(raw);
        }

        private MeetingMinutes existingMinutes() {
            return minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "기존 요약",
                    TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON, OPEN_ISSUES_JSON,
                    false, null, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
        }

        @Test
        @DisplayName("title만 수정하면 title만 갱신되고 나머지 필드는 기존 값을 유지한다")
        void updateMinutes_updatesOnlyTitle() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            MinutesDetailDto result = reportService.updateMinutes(OWNER_ID, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("새 제목");
            assertThat(result.summary()).isEqualTo("기존 요약");
            assertThat(result.topics()).isEqualTo(TOPICS_JSON);
            assertThat(result.decisions()).isEqualTo(DECISIONS_JSON);
            assertThat(result.actionItems()).isEqualTo(ACTION_ITEMS_JSON);
            assertThat(result.openIssues()).isEqualTo(OPEN_ISSUES_JSON);
            verify(meetingMinutesRepository).updateTitle(MEETING_ID, "새 제목");
            verify(meetingMinutesRepository, never()).updateSummary(any(), any());
            verify(meetingMinutesRepository, never()).updateTopics(any(), any());
            verify(meetingMinutesRepository, never()).updateDecisions(any(), any());
            verify(meetingMinutesRepository, never()).updateActionItems(any(), any());
            verify(meetingMinutesRepository, never()).updateOpenIssues(any(), any());
        }

        @Test
        @DisplayName("topics만 수정하면 기존 배열이 새 배열로 완전히 교체된다")
        void updateMinutes_updatesOnlyTopics() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request =
                    new RequestUpdateMinutesDto(null, null, json(NEW_TOPICS_JSON), null, null, null);

            MinutesDetailDto result = reportService.updateMinutes(OWNER_ID, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("스프린트 리뷰 회의록");
            assertThat(result.topics()).isEqualTo(NEW_TOPICS_JSON);
            assertThat(result.decisions()).isEqualTo(DECISIONS_JSON);
            verify(meetingMinutesRepository).updateTopics(MEETING_ID, NEW_TOPICS_JSON);
            verify(meetingMinutesRepository, never()).updateTitle(any(), any());
            verify(meetingMinutesRepository, never()).updateDecisions(any(), any());
        }

        @Test
        @DisplayName("여러 필드를 동시에 보내면 그 필드들만 모두 갱신된다(빈 배열도 유효한 값으로 반영)")
        void updateMinutes_updatesMultipleFields() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto(
                    "새 제목", "새 요약", json(NEW_TOPICS_JSON), null, null, json("[]"));

            MinutesDetailDto result = reportService.updateMinutes(OWNER_ID, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("새 제목");
            assertThat(result.summary()).isEqualTo("새 요약");
            assertThat(result.topics()).isEqualTo(NEW_TOPICS_JSON);
            assertThat(result.decisions()).isEqualTo(DECISIONS_JSON);
            assertThat(result.actionItems()).isEqualTo(ACTION_ITEMS_JSON);
            assertThat(result.openIssues()).isEqualTo("[]");
            verify(meetingMinutesRepository).updateTitle(MEETING_ID, "새 제목");
            verify(meetingMinutesRepository).updateSummary(MEETING_ID, "새 요약");
            verify(meetingMinutesRepository).updateTopics(MEETING_ID, NEW_TOPICS_JSON);
            verify(meetingMinutesRepository).updateOpenIssues(MEETING_ID, "[]");
            verify(meetingMinutesRepository, never()).updateDecisions(any(), any());
            verify(meetingMinutesRepository, never()).updateActionItems(any(), any());
        }

        @Test
        @DisplayName("아무 필드도 보내지 않으면 DB 쓰기 없이 현재 값 그대로 반환한다")
        void updateMinutes_noFieldsSkipsWrite() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto(null, null, null, null, null, null);

            MinutesDetailDto result = reportService.updateMinutes(OWNER_ID, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("스프린트 리뷰 회의록");
            assertThat(result.summary()).isEqualTo("기존 요약");
            assertThat(result.topics()).isEqualTo(TOPICS_JSON);
            verify(meetingMinutesRepository, never()).updateTitle(any(), any());
            verify(meetingMinutesRepository, never()).updateSummary(any(), any());
            verify(meetingMinutesRepository, never()).updateTopics(any(), any());
            verify(meetingMinutesRepository, never()).updateDecisions(any(), any());
            verify(meetingMinutesRepository, never()).updateActionItems(any(), any());
            verify(meetingMinutesRepository, never()).updateOpenIssues(any(), any());
        }

        @Test
        @DisplayName("title에 빈 문자열을 전달하면 VALIDATION_FAILED 예외가 발생하고 아무 필드도 갱신되지 않는다")
        void updateMinutes_throwsWhenTitleIsBlank() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("   ", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(OWNER_ID, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.VALIDATION_FAILED);
            verify(meetingMinutesRepository, never()).updateTitle(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·회의록 조회를 시도하지 않는다")
        void updateMinutes_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(OWNER_ID, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 GUEST면 MINUTES_EDIT_DENIED 예외가 발생하고 회의록 조회를 시도하지 않는다")
        void updateMinutes_throwsWhenRequesterIsGuest() {
            Long guestUserId = 77L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, guestUserId))
                    .willReturn(Optional.of(guestMember(guestUserId)));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(guestUserId, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_EDIT_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 스페이스 멤버가 아니면 MINUTES_EDIT_DENIED 예외가 발생한다")
        void updateMinutes_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(Optional.empty());
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(nonMemberId, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_EDIT_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("회의는 있지만 회의록이 없으면 MINUTES_NOT_FOUND 예외가 발생한다")
        void updateMinutes_throwsWhenMinutesNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.empty());
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(OWNER_ID, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_NOT_FOUND);
        }

        @Test
        @DisplayName("OWNER는 정상적으로 회의록을 수정할 수 있다")
        void updateMinutes_allowsOwner() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            MinutesDetailDto result = reportService.updateMinutes(OWNER_ID, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("새 제목");
        }

        @Test
        @DisplayName("MEMBER는 정상적으로 회의록을 수정할 수 있다")
        void updateMinutes_allowsMember() {
            Long memberUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = existingMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, memberUserId))
                    .willReturn(Optional.of(memberRoleMember(memberUserId)));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("새 제목", null, null, null, null, null);

            MinutesDetailDto result = reportService.updateMinutes(memberUserId, MEETING_ID, request);

            assertThat(result.title()).isEqualTo("새 제목");
        }

        @Test
        @DisplayName("확정된 회의록은 필드 값과 무관하게 MINUTES_ALREADY_CONFIRMED 예외로 즉시 거부된다")
        void updateMinutes_throwsWhenAlreadyConfirmed() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes confirmedMinutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "기존 요약",
                    TOPICS_JSON, DECISIONS_JSON, ACTION_ITEMS_JSON, OPEN_ISSUES_JSON,
                    true, OffsetDateTime.parse("2026-08-05T02:00:00Z"),
                    OffsetDateTime.parse("2026-08-04T05:10:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(confirmedMinutes));
            // 빈 문자열(원래대로면 VALIDATION_FAILED 대상)이어도 확정 체크가 먼저 걸려야 한다.
            RequestUpdateMinutesDto request = new RequestUpdateMinutesDto("", null, null, null, null, null);

            assertThatThrownBy(() -> reportService.updateMinutes(OWNER_ID, MEETING_ID, request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_ALREADY_CONFIRMED);
            verify(meetingMinutesRepository, never()).updateTitle(any(), any());
        }
    }

    @Nested
    @DisplayName("REPORTS-07 회의록 확정")
    class ConfirmMinutes {

        private MeetingMinutes unconfirmedMinutes() {
            return minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "요약", "[]", "[]", "[]", "[]",
                    false, null, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
        }

        @Test
        @DisplayName("미확정 회의록을 확정하면 isConfirmed=true로 바뀌고 confirmedAt이 채워지며 UPDATE가 호출된다")
        void confirmMinutes_confirmsWhenNotYetConfirmed() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = unconfirmedMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            ResponseConfirmMinutesDto result = reportService.confirmMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.isConfirmed()).isTrue();
            assertThat(result.confirmedAt()).isNotNull();

            ArgumentCaptor<OffsetDateTime> captor = ArgumentCaptor.forClass(OffsetDateTime.class);
            verify(meetingMinutesRepository).confirm(eq(MEETING_ID), captor.capture());
            // 응답의 confirmedAt과 UPDATE에 실제로 넘긴 값이 같은 인스턴스(재조회 없이 동일 값 재사용)인지 확인.
            assertThat(captor.getValue()).isEqualTo(result.confirmedAt());
        }

        @Test
        @DisplayName("이미 확정된 회의록이면 쓰기 없이 기존 confirmedAt 그대로 반환한다(멱등)")
        void confirmMinutes_idempotentWhenAlreadyConfirmed() {
            OffsetDateTime existingConfirmedAt = OffsetDateTime.parse("2026-08-05T02:00:00Z");
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = minutesOf(
                    MEETING_ID, "스프린트 리뷰 회의록", "요약", "[]", "[]", "[]", "[]",
                    true, existingConfirmedAt, OffsetDateTime.parse("2026-08-04T05:10:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            ResponseConfirmMinutesDto result = reportService.confirmMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.isConfirmed()).isTrue();
            assertThat(result.confirmedAt()).isEqualTo(existingConfirmedAt);
            verify(meetingMinutesRepository, never()).confirm(any(), any());
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·회의록 조회를 시도하지 않는다")
        void confirmMinutes_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.confirmMinutes(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 GUEST면 MINUTES_EDIT_DENIED 예외가 발생하고 회의록 조회를 시도하지 않는다")
        void confirmMinutes_throwsWhenRequesterIsGuest() {
            Long guestUserId = 77L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, guestUserId))
                    .willReturn(Optional.of(guestMember(guestUserId)));

            assertThatThrownBy(() -> reportService.confirmMinutes(guestUserId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_EDIT_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("요청자가 스페이스 멤버가 아니면 MINUTES_EDIT_DENIED 예외가 발생한다")
        void confirmMinutes_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.confirmMinutes(nonMemberId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_EDIT_DENIED);
            verifyNoInteractions(meetingMinutesRepository);
        }

        @Test
        @DisplayName("회의는 있지만 회의록이 없으면 MINUTES_NOT_FOUND 예외가 발생한다")
        void confirmMinutes_throwsWhenMinutesNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.confirmMinutes(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MINUTES_NOT_FOUND);
        }

        @Test
        @DisplayName("OWNER는 정상적으로 회의록을 확정할 수 있다")
        void confirmMinutes_allowsOwner() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = unconfirmedMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(Optional.of(ownerMember()));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            ResponseConfirmMinutesDto result = reportService.confirmMinutes(OWNER_ID, MEETING_ID);

            assertThat(result.isConfirmed()).isTrue();
        }

        @Test
        @DisplayName("MEMBER는 정상적으로 회의록을 확정할 수 있다")
        void confirmMinutes_allowsMember() {
            Long memberUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            MeetingMinutes minutes = unconfirmedMinutes();
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.findByTeamIdAndUserId(SPACE_ID, memberUserId))
                    .willReturn(Optional.of(memberRoleMember(memberUserId)));
            given(meetingMinutesRepository.findById(MEETING_ID)).willReturn(Optional.of(minutes));

            ResponseConfirmMinutesDto result = reportService.confirmMinutes(memberUserId, MEETING_ID);

            assertThat(result.isConfirmed()).isTrue();
        }
    }

    @Nested
    @DisplayName("REPORTS-09 퍼실리테이터 리포트 목록 조회")
    class GetFacilitatorReports {

        @Test
        @DisplayName("정상 조회 시 Page 결과가 PageResponse 필드로 정확히 매핑된다")
        void getFacilitatorReports_returnsMappedPageResponse() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<FacilitatorReportSummaryDto> content = List.of(
                    new FacilitatorReportSummaryDto(
                            34L, "8월 4주차 스프린트 회의", "8월 4주차 회의 퍼실리테이션 리포트", "SPRINT_REVIEW",
                            OffsetDateTime.parse("2026-08-04T05:15:00Z")),
                    new FacilitatorReportSummaryDto(
                            33L, "7월 회고", "7월 회고 퍼실리테이션 리포트", "RETROSPECTIVE",
                            OffsetDateTime.parse("2026-07-28T05:15:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            // PageImpl은 offset+pageSize가 total을 넘으면 total을 content.size()로 되돌려버리므로
            // (GetMinutesList 테스트와 동일한 이유), pageSize(10) 이하로 total을 주면 안 되고 12처럼 더 크게 준다.
            Page<FacilitatorReportSummaryDto> page = new PageImpl<>(content, pageable, 12);
            given(facilitatorReportRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<FacilitatorReportSummaryDto> result =
                    reportService.getFacilitatorReports(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).hasSize(2);
            assertThat(result.content().get(0).meetingId()).isEqualTo(34L);
            assertThat(result.content().get(0).meetingRoomName()).isEqualTo("8월 4주차 스프린트 회의");
            assertThat(result.content().get(0).title()).isEqualTo("8월 4주차 회의 퍼실리테이션 리포트");
            assertThat(result.content().get(0).meetingType()).isEqualTo("SPRINT_REVIEW");
            assertThat(result.page()).isEqualTo(0);
            assertThat(result.size()).isEqualTo(10);
            assertThat(result.totalElements()).isEqualTo(12);
            assertThat(result.totalPages()).isEqualTo(2);
            assertThat(result.hasNext()).isTrue();
        }

        @Test
        @DisplayName("결과가 없으면 빈 content를 반환한다(예외 아님)")
        void getFacilitatorReports_returnsEmptyContentWhenNoneExist() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<FacilitatorReportSummaryDto> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(facilitatorReportRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(emptyPage);

            PageResponse<FacilitatorReportSummaryDto> result =
                    reportService.getFacilitatorReports(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0);
            assertThat(result.totalPages()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        @DisplayName("meetingType이 null인 항목도 그대로 null로 매핑된다")
        void getFacilitatorReports_mapsNullMeetingType() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            List<FacilitatorReportSummaryDto> content = List.of(
                    new FacilitatorReportSummaryDto(
                            34L, "8월 4주차 스프린트 회의", "8월 4주차 회의 퍼실리테이션 리포트", null,
                            OffsetDateTime.parse("2026-08-04T05:15:00Z")));
            Pageable pageable = PageRequest.of(0, 10);
            Page<FacilitatorReportSummaryDto> page = new PageImpl<>(content, pageable, 1);
            given(facilitatorReportRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<FacilitatorReportSummaryDto> result =
                    reportService.getFacilitatorReports(OWNER_ID, SPACE_ID, pageable);

            assertThat(result.content().get(0).meetingType()).isNull();
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 SPACE_NOT_FOUND 예외가 발생하고 멤버·리포트 조회를 시도하지 않는다")
        void getFacilitatorReports_throwsWhenSpaceNotFound() {
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getFacilitatorReports(OWNER_ID, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(facilitatorReportRepository);
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 리포트 조회를 시도하지 않는다")
        void getFacilitatorReports_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() ->
                    reportService.getFacilitatorReports(nonMemberId, SPACE_ID, PageRequest.of(0, 10)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(facilitatorReportRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getFacilitatorReports_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);

            Pageable pageable = PageRequest.of(0, 10);
            Page<FacilitatorReportSummaryDto> page = new PageImpl<>(List.of(), pageable, 0);
            given(facilitatorReportRepository.findAllByTeamId(SPACE_ID, pageable)).willReturn(page);

            PageResponse<FacilitatorReportSummaryDto> result =
                    reportService.getFacilitatorReports(guestUserId, SPACE_ID, pageable);

            assertThat(result.content()).isEmpty();
        }

        @Test
        @DisplayName("page=1, size=5 요청 시 Repository에 동일한 페이지 파라미터가 그대로 전달된다")
        void getFacilitatorReports_passesPageableToRepository() {
            Team team = activeTeam(SPACE_ID, OWNER_ID);
            given(teamRepository.findByIdAndIsDeletedFalse(SPACE_ID)).willReturn(Optional.of(team));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);

            Pageable requested = PageRequest.of(1, 5);
            Page<FacilitatorReportSummaryDto> page = new PageImpl<>(List.of(), requested, 0);
            given(facilitatorReportRepository.findAllByTeamId(eq(SPACE_ID), any(Pageable.class))).willReturn(page);

            reportService.getFacilitatorReports(OWNER_ID, SPACE_ID, requested);

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(facilitatorReportRepository).findAllByTeamId(eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(1);
            assertThat(captor.getValue().getPageSize()).isEqualTo(5);
        }
    }

    @Nested
    @DisplayName("REPORTS-10 퍼실리테이터 리포트 상세 조회")
    class GetFacilitatorReport {

        private static final String PARTICIPATION_STATS_JSON =
                "[{\"speaker\":\"ssong123\",\"talkTimeRatio\":0.3}]";
        private static final String QUALITY_EVALUATION_JSON = "{\"score\":4,\"criteria\":[]}";
        private static final String STRENGTHS_JSON = "[{\"point\":\"안건별 시간 배분이 적절했음\"}]";
        private static final String IMPROVEMENTS_JSON = "[{\"point\":\"소극적인 참가자 발언 유도 필요\"}]";
        private static final String DECISION_PROCESS_CHECKS_JSON =
                "[{\"check\":\"결정사항에 대한 합의 절차 확인됨\"}]";
        private static final String UNRESOLVED_ISSUES_EVALUATION_JSON =
                "[{\"issue\":\"S3 설정 이슈는 다음 회의로 이월\"}]";
        private static final String NEXT_MEETING_SUGGESTIONS_JSON =
                "[{\"suggestion\":\"다음 회의는 30분 내로 단축 권장\"}]";

        @Test
        @DisplayName("정상 조회 시 7개 JSONB 필드가 JSON 원문 그대로 응답 DTO에 매핑된다")
        void getFacilitatorReport_returnsMappedDetail() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-04T05:15:00Z");
            FacilitatorReport report = facilitatorReportOf(
                    MEETING_ID, "8월 4주차 회의 퍼실리테이션 리포트", "SPRINT_REVIEW",
                    "전반적으로 안건 진행이 원활했습니다.", "일부 참가자의 발언 비중이 낮았습니다.",
                    PARTICIPATION_STATS_JSON, QUALITY_EVALUATION_JSON, STRENGTHS_JSON, IMPROVEMENTS_JSON,
                    DECISION_PROCESS_CHECKS_JSON, UNRESOLVED_ISSUES_EVALUATION_JSON, NEXT_MEETING_SUGGESTIONS_JSON,
                    createdAt);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(facilitatorReportRepository.findById(MEETING_ID)).willReturn(Optional.of(report));

            FacilitatorReportDetailDto result = reportService.getFacilitatorReport(OWNER_ID, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
            assertThat(result.title()).isEqualTo("8월 4주차 회의 퍼실리테이션 리포트");
            assertThat(result.meetingType()).isEqualTo("SPRINT_REVIEW");
            assertThat(result.overallReview()).isEqualTo("전반적으로 안건 진행이 원활했습니다.");
            assertThat(result.participationComment()).isEqualTo("일부 참가자의 발언 비중이 낮았습니다.");
            assertThat(result.participationStats()).isEqualTo(PARTICIPATION_STATS_JSON);
            assertThat(result.qualityEvaluation()).isEqualTo(QUALITY_EVALUATION_JSON);
            assertThat(result.strengths()).isEqualTo(STRENGTHS_JSON);
            assertThat(result.improvements()).isEqualTo(IMPROVEMENTS_JSON);
            assertThat(result.decisionProcessChecks()).isEqualTo(DECISION_PROCESS_CHECKS_JSON);
            assertThat(result.unresolvedIssuesEvaluation()).isEqualTo(UNRESOLVED_ISSUES_EVALUATION_JSON);
            assertThat(result.nextMeetingSuggestions()).isEqualTo(NEXT_MEETING_SUGGESTIONS_JSON);
            assertThat(result.createdAt()).isEqualTo(createdAt);
        }

        @Test
        @DisplayName("meetingType이 null이면 그대로 null로 매핑된다")
        void getFacilitatorReport_mapsNullMeetingType() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            FacilitatorReport report = facilitatorReportOf(
                    MEETING_ID, "8월 4주차 회의 퍼실리테이션 리포트", null,
                    "전반적으로 안건 진행이 원활했습니다.", "일부 참가자의 발언 비중이 낮았습니다.",
                    PARTICIPATION_STATS_JSON, QUALITY_EVALUATION_JSON, STRENGTHS_JSON, IMPROVEMENTS_JSON,
                    DECISION_PROCESS_CHECKS_JSON, UNRESOLVED_ISSUES_EVALUATION_JSON, NEXT_MEETING_SUGGESTIONS_JSON,
                    OffsetDateTime.parse("2026-08-04T05:15:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(facilitatorReportRepository.findById(MEETING_ID)).willReturn(Optional.of(report));

            FacilitatorReportDetailDto result = reportService.getFacilitatorReport(OWNER_ID, MEETING_ID);

            assertThat(result.meetingType()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 회의면 MEETING_NOT_FOUND 예외가 발생하고 멤버·리포트 조회를 시도하지 않는다")
        void getFacilitatorReport_throwsWhenMeetingNotFound() {
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getFacilitatorReport(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.MEETING_NOT_FOUND);
            verifyNoInteractions(memberRepository);
            verifyNoInteractions(facilitatorReportRepository);
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 SPACE_ACCESS_DENIED 예외가 발생하고 리포트 조회를 시도하지 않는다")
        void getFacilitatorReport_throwsWhenRequesterIsNotMember() {
            Long nonMemberId = 99L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, nonMemberId)).willReturn(false);

            assertThatThrownBy(() -> reportService.getFacilitatorReport(nonMemberId, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.SPACE_ACCESS_DENIED);
            verifyNoInteractions(facilitatorReportRepository);
        }

        @Test
        @DisplayName("GUEST 등 OWNER가 아닌 멤버도 정상 조회된다(오너 제한 없음)")
        void getFacilitatorReport_allowsNonOwnerMemberRequester() {
            Long guestUserId = 55L;
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            FacilitatorReport report = facilitatorReportOf(
                    MEETING_ID, "8월 4주차 회의 퍼실리테이션 리포트", "SPRINT_REVIEW",
                    "전반적으로 안건 진행이 원활했습니다.", "일부 참가자의 발언 비중이 낮았습니다.",
                    PARTICIPATION_STATS_JSON, QUALITY_EVALUATION_JSON, STRENGTHS_JSON, IMPROVEMENTS_JSON,
                    DECISION_PROCESS_CHECKS_JSON, UNRESOLVED_ISSUES_EVALUATION_JSON, NEXT_MEETING_SUGGESTIONS_JSON,
                    OffsetDateTime.parse("2026-08-04T05:15:00Z"));
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, guestUserId)).willReturn(true);
            given(facilitatorReportRepository.findById(MEETING_ID)).willReturn(Optional.of(report));

            FacilitatorReportDetailDto result = reportService.getFacilitatorReport(guestUserId, MEETING_ID);

            assertThat(result.meetingId()).isEqualTo(MEETING_ID);
        }

        @Test
        @DisplayName("회의는 있지만 리포트가 없으면(아직 생성 안 됨) FACILITATOR_REPORT_NOT_FOUND 예외가 발생한다")
        void getFacilitatorReport_throwsWhenReportNotFound() {
            MeetingRoom meetingRoom = meetingRoomOf(MEETING_ID, SPACE_ID);
            given(meetingRoomRepository.findById(MEETING_ID)).willReturn(Optional.of(meetingRoom));
            given(memberRepository.existsByTeamIdAndUserId(SPACE_ID, OWNER_ID)).willReturn(true);
            given(facilitatorReportRepository.findById(MEETING_ID)).willReturn(Optional.empty());

            assertThatThrownBy(() -> reportService.getFacilitatorReport(OWNER_ID, MEETING_ID))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.FACILITATOR_REPORT_NOT_FOUND);
        }
    }
}
