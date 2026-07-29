package com.ssafy.backend.meeting.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.meeting.dto.RequestTransferHostDto;
import com.ssafy.backend.meeting.dto.ResponseTransferHostDto;
import com.ssafy.backend.meeting.service.MeetingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MEET-06 호스트 양도 API의 요청 매핑·입력 검증·공통 응답·예외 변환을 검증하는 Controller 테스트.
 *
 * <p>검증 내용:</p>
 * <ul>
 *     <li>JWT principal의 userId와 meetingId, nextHostParticipantId를 Service에 전달하는지 확인한다.</li>
 *     <li>정상 양도 시 변경 전·후 hostId를 포함한 200 SUCCESS 응답을 반환하는지 확인한다.</li>
 *     <li>대상 참여자 ID가 없으면 400, 비호스트 요청이면 403, 회의가 없으면 404인지 확인한다.</li>
 * </ul>
 *
 * <p>MeetingService는 Mock으로 대체하고 standalone MockMvc로 웹 계층만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MeetingController MEET-06 슬라이스 테스트")
class MeetingControllerTest {

    private static final String USER_ID = "100";
    private static final Long MEETING_ID = 1L;
    private static final Long NEXT_HOST_PARTICIPANT_ID = 20L;

    @Mock
    private MeetingService meetingService;

    @InjectMocks
    private MeetingController meetingController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(meetingController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of())
        );
    }

    @Nested
    @DisplayName("MEET-06 POST /api/v1/meetings/{meetingId}/grant")
    class TransferHost {

        @Test
        @DisplayName("유효한 요청이면 호스트를 양도하고 200 SUCCESS를 반환한다")
        void transferHost_returns200WithChangedHost() throws Exception {
            RequestTransferHostDto request = new RequestTransferHostDto(NEXT_HOST_PARTICIPANT_ID);
            ResponseTransferHostDto response = new ResponseTransferHostDto(MEETING_ID, 100L, 200L);
            given(meetingService.transferHost(eq(100L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                    .willReturn(response);

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("호스트 권한이 양도되었습니다."))
                    .andExpect(jsonPath("$.data.meetingId").value(MEETING_ID))
                    .andExpect(jsonPath("$.data.previousHostId").value(100L))
                    .andExpect(jsonPath("$.data.nextHostId").value(200L));

            verify(meetingService)
                    .transferHost(eq(100L), eq(MEETING_ID), any(RequestTransferHostDto.class));
        }

        @Test
        @DisplayName("새 호스트 참여자 ID가 없으면 400 VALIDATION_FAILED를 반환한다")
        void transferHost_returns400WhenNextHostParticipantIdIsNull() throws Exception {
            String requestBody = """
                    {
                      "nextHostParticipantId": null
                    }
                    """;

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("요청자가 현재 호스트가 아니면 403 MEETING_HOST_REQUIRED를 반환한다")
        void transferHost_returns403WhenRequesterIsNotHost() throws Exception {
            RequestTransferHostDto request = new RequestTransferHostDto(NEXT_HOST_PARTICIPANT_ID);
            given(meetingService.transferHost(eq(100L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                    .willThrow(new CustomException(ErrorCode.MEETING_HOST_REQUIRED));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("MEETING_HOST_REQUIRED"));
        }

        @Test
        @DisplayName("회의가 없거나 삭제됐으면 404 MEETING_NOT_FOUND를 반환한다")
        void transferHost_returns404WhenMeetingNotFound() throws Exception {
            RequestTransferHostDto request = new RequestTransferHostDto(NEXT_HOST_PARTICIPANT_ID);
            given(meetingService.transferHost(eq(100L), eq(MEETING_ID), any(RequestTransferHostDto.class)))
                    .willThrow(new CustomException(ErrorCode.MEETING_NOT_FOUND));

            mockMvc.perform(post("/api/v1/meetings/{meetingId}/grant", MEETING_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("MEETING_NOT_FOUND"));
        }
    }
}
