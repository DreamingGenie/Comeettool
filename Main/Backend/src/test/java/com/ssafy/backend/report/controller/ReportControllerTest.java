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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ssafy.backend.global.common.PageResponse;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.report.dto.TranscriptSummaryDto;
import com.ssafy.backend.report.service.ReportService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
