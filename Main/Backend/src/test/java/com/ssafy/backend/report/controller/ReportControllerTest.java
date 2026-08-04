package com.ssafy.backend.report.controller;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.report.dto.MinutesSummaryDto;
import com.ssafy.backend.report.dto.RequestExportDto;
import com.ssafy.backend.report.dto.ResponseExportDto;
import com.ssafy.backend.report.dto.TranscriptDetailDto;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.service.ReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ReportController 슬라이스 테스트 (standalone MockMvc).
 * ReportService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 * Pageable 바인딩은 standalone 환경에 기본 등록되지 않아 PageableHandlerMethodArgumentResolver를 직접 추가한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReportController 슬라이스 테스트")
class ReportControllerTest {

    private static final String USER_ID = "1";
    private static final Long SPACE_ID = 10L;

    @Mock
    private ReportService reportService;

    @InjectMocks
    private ReportController reportController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(reportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(
                        new AuthenticationPrincipalArgumentResolver(),
                        new PageableHandlerMethodArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Nested
    @DisplayName("REPORTS-01 GET /api/v1/spaces/{spaceId}/reports/transcripts")
    class GetTranscripts {

        @Test
        @DisplayName("정상 조회면 200 SUCCESS, message='전사 목록 조회 성공', data에 content·페이지 메타를 반환한다")
        void getTranscripts_returns200() throws Exception {
            List<TranscriptSummaryDto> content = List.of(
                    new TranscriptSummaryDto(34L, "8월 4주차 스프린트 회의", OffsetDateTime.parse("2026-08-04T05:00:00Z")));
            PageResponse<TranscriptSummaryDto> response = new PageResponse<>(content, 0, 10, 23, 3, true);
            given(reportService.getTranscripts(eq(1L), eq(SPACE_ID), any(Pageable.class))).willReturn(response);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/transcripts", SPACE_ID)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("전사 목록 조회 성공"))
                    .andExpect(jsonPath("$.data.content[0].meetingId").value(34))
                    .andExpect(jsonPath("$.data.content[0].meetingRoomName").value("8월 4주차 스프린트 회의"))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(23))
                    .andExpect(jsonPath("$.data.totalPages").value(3))
                    .andExpect(jsonPath("$.data.hasNext").value(true));

            verify(reportService).getTranscripts(eq(1L), eq(SPACE_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("쿼리 파라미터를 지정하지 않으면 page=0, size=10 기본값이 적용된다")
        void getTranscripts_appliesDefaultPageable() throws Exception {
            PageResponse<TranscriptSummaryDto> response = new PageResponse<>(List.of(), 0, 10, 0, 0, false);
            given(reportService.getTranscripts(eq(1L), eq(SPACE_ID), any(Pageable.class))).willReturn(response);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/transcripts", SPACE_ID))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reportService).getTranscripts(eq(1L), eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void getTranscripts_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(reportService).getTranscripts(eq(1L), eq(SPACE_ID), any(Pageable.class));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/transcripts", SPACE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void getTranscripts_returns403WhenNotMember() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED))
                    .when(reportService).getTranscripts(eq(1L), eq(SPACE_ID), any(Pageable.class));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/transcripts", SPACE_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }
    }

    @Nested
    @DisplayName("REPORTS-02 GET /api/v1/meetings/{meetingId}/reports/transcript")
    class GetTranscript {

        private static final Long MEETING_ID = 34L;

        @Test
        @DisplayName("정상 조회면 200 SUCCESS, message='전사 조회 성공', transcript가 이중 직렬화 없이 배열로 반환된다")
        void getTranscript_returns200() throws Exception {
            String transcriptJson =
                    "[{\"speaker\":\"ssong123\",\"start\":12.5,\"end\":15.8,\"text\":\"그럼 다음 안건으로 넘어가겠습니다\"}]";
            TranscriptDetailDto response = new TranscriptDetailDto(
                    MEETING_ID, transcriptJson, OffsetDateTime.parse("2026-08-04T05:00:00Z"));
            given(reportService.getTranscript(1L, MEETING_ID)).willReturn(response);

            mockMvc.perform(get("/api/v1/meetings/{meetingId}/reports/transcript", MEETING_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("전사 조회 성공"))
                    .andExpect(jsonPath("$.data.meetingId").value(MEETING_ID))
                    // transcript가 문자열로 다시 이스케이프되지 않고 실제 JSON 배열로 파싱되는지 확인(이중 직렬화 방지 검증).
                    .andExpect(jsonPath("$.data.transcript").isArray())
                    .andExpect(jsonPath("$.data.transcript", hasSize(1)))
                    .andExpect(jsonPath("$.data.transcript[0].speaker").value("ssong123"))
                    .andExpect(jsonPath("$.data.transcript[0].start").value(12.5))
                    .andExpect(jsonPath("$.data.transcript[0].end").value(15.8))
                    .andExpect(jsonPath("$.data.transcript[0].text").value("그럼 다음 안건으로 넘어가겠습니다"))
                    .andExpect(jsonPath("$.data.createdAt").value("2026-08-04T05:00:00Z"));

            verify(reportService).getTranscript(1L, MEETING_ID);
        }

        @Test
        @DisplayName("존재하지 않는 회의면 404 MEETING_NOT_FOUND를 반환한다")
        void getTranscript_returns404WhenMeetingNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND))
                    .when(reportService).getTranscript(1L, MEETING_ID);

            mockMvc.perform(get("/api/v1/meetings/{meetingId}/reports/transcript", MEETING_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void getTranscript_returns403WhenNotMember() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED))
                    .when(reportService).getTranscript(1L, MEETING_ID);

            mockMvc.perform(get("/api/v1/meetings/{meetingId}/reports/transcript", MEETING_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("회의는 있지만 전사가 없으면 404 TRANSCRIPT_NOT_FOUND를 반환한다(MEETING_NOT_FOUND와 구분)")
        void getTranscript_returns404WhenTranscriptNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.TRANSCRIPT_NOT_FOUND))
                    .when(reportService).getTranscript(1L, MEETING_ID);

            mockMvc.perform(get("/api/v1/meetings/{meetingId}/reports/transcript", MEETING_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("REPORTS-03 POST /api/v1/meetings/{meetingId}/reports/transcript/export")
    class ExportTranscript {

        private static final Long MEETING_ID = 34L;

        @Test
        @DisplayName("정상 내보내기면 200 SUCCESS, message='전사 내보내기 성공', data 필드를 반환한다")
        void exportTranscript_returns200() throws Exception {
            RequestExportDto request = new RequestExportDto("md");
            ResponseExportDto response =
                    new ResponseExportDto(MEETING_ID, "md", "https://bucket.s3.region.amazonaws.com/transcripts/34.md");
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willReturn(response);

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("전사 내보내기 성공"))
                    .andExpect(jsonPath("$.data.meetingId").value(34))
                    .andExpect(jsonPath("$.data.format").value("md"))
                    .andExpect(jsonPath("$.data.url").value("https://bucket.s3.region.amazonaws.com/transcripts/34.md"));

            verify(reportService).exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class));
        }

        @Test
        @DisplayName("format이 md/pdf가 아니면 400 VALIDATION_FAILED를 반환한다")
        void exportTranscript_returns400WhenFormatIsInvalid() throws Exception {
            RequestExportDto request = new RequestExportDto("docx");
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willThrow(new CustomException(ErrorCode.VALIDATION_FAILED));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("format을 생략하면 400 VALIDATION_FAILED를 반환한다")
        void exportTranscript_returns400WhenFormatIsMissing() throws Exception {
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willThrow(new CustomException(ErrorCode.VALIDATION_FAILED));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("존재하지 않는 회의면 404 MEETING_NOT_FOUND를 반환한다")
        void exportTranscript_returns404WhenMeetingNotFound() throws Exception {
            RequestExportDto request = new RequestExportDto("md");
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 회의가 속한 스페이스의 멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void exportTranscript_returns403WhenNotMember() throws Exception {
            RequestExportDto request = new RequestExportDto("md");
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }

        @Test
        @DisplayName("회의는 있지만 전사가 없으면 404 TRANSCRIPT_NOT_FOUND를 반환한다")
        void exportTranscript_returns404WhenTranscriptNotFound() throws Exception {
            RequestExportDto request = new RequestExportDto("md");
            given(reportService.exportTranscript(eq(1L), eq(MEETING_ID), any(RequestExportDto.class)))
                    .willThrow(new CustomException(ErrorCode.TRANSCRIPT_NOT_FOUND));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/reports/transcript/export", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TRANSCRIPT_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("REPORTS-04 GET /api/v1/spaces/{spaceId}/reports/minutes")
    class GetMinutesList {

        @Test
        @DisplayName("정상 조회면 200 SUCCESS, message='회의록 목록 조회 성공', data에 content(isConfirmed 포함)·페이지 메타를 반환한다")
        void getMinutesList_returns200() throws Exception {
            List<MinutesSummaryDto> content = List.of(
                    new MinutesSummaryDto(
                            34L, "8월 4주차 스프린트 회의", "스프린트 리뷰 회의록", false,
                            OffsetDateTime.parse("2026-08-04T05:10:00Z")));
            PageResponse<MinutesSummaryDto> response = new PageResponse<>(content, 0, 10, 12, 2, true);
            given(reportService.getMinutesList(eq(1L), eq(SPACE_ID), any(Pageable.class))).willReturn(response);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/minutes", SPACE_ID)
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("회의록 목록 조회 성공"))
                    .andExpect(jsonPath("$.data.content[0].meetingId").value(34))
                    .andExpect(jsonPath("$.data.content[0].meetingRoomName").value("8월 4주차 스프린트 회의"))
                    .andExpect(jsonPath("$.data.content[0].title").value("스프린트 리뷰 회의록"))
                    .andExpect(jsonPath("$.data.content[0].isConfirmed").value(false))
                    .andExpect(jsonPath("$.data.page").value(0))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.totalElements").value(12))
                    .andExpect(jsonPath("$.data.totalPages").value(2))
                    .andExpect(jsonPath("$.data.hasNext").value(true));

            verify(reportService).getMinutesList(eq(1L), eq(SPACE_ID), any(Pageable.class));
        }

        @Test
        @DisplayName("쿼리 파라미터를 지정하지 않으면 page=0, size=10 기본값이 적용된다")
        void getMinutesList_appliesDefaultPageable() throws Exception {
            PageResponse<MinutesSummaryDto> response = new PageResponse<>(List.of(), 0, 10, 0, 0, false);
            given(reportService.getMinutesList(eq(1L), eq(SPACE_ID), any(Pageable.class))).willReturn(response);

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/minutes", SPACE_ID))
                    .andExpect(status().isOk());

            ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
            verify(reportService).getMinutesList(eq(1L), eq(SPACE_ID), captor.capture());
            assertThat(captor.getValue().getPageNumber()).isEqualTo(0);
            assertThat(captor.getValue().getPageSize()).isEqualTo(10);
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void getMinutesList_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(reportService).getMinutesList(eq(1L), eq(SPACE_ID), any(Pageable.class));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/minutes", SPACE_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 해당 스페이스 멤버가 아니면 403 SPACE_ACCESS_DENIED를 반환한다")
        void getMinutesList_returns403WhenNotMember() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_ACCESS_DENIED))
                    .when(reportService).getMinutesList(eq(1L), eq(SPACE_ID), any(Pageable.class));

            mockMvc.perform(get("/api/v1/spaces/{spaceId}/reports/minutes", SPACE_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_ACCESS_DENIED"));
        }
    }
}
