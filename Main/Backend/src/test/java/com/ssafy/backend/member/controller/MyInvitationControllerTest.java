package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.member.dto.ResponseMyInvitationDto;
import com.ssafy.backend.member.service.InvitationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MyInvitationController 슬라이스 테스트 (standalone MockMvc).
 * InvitationService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 * 토큰 자체가 없는 401 케이스는 실제 필터 체인이 필요해 JwtAuthenticationFilterTest(@SpringBootTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MyInvitationController 슬라이스 테스트")
class MyInvitationControllerTest {

    private static final String USER_ID = "5";

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private MyInvitationController myInvitationController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(myInvitationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Nested
    @DisplayName("MEMBER-03 GET /api/v1/invitations/me")
    class FindMyInvitations {

        @Test
        @DisplayName("받은 초대가 있으면 200과 목록을 반환한다")
        void findMyInvitations_returns200WithList() throws Exception {
            ResponseMyInvitationDto item = new ResponseMyInvitationDto(
                    "3f2504e0-4f89-11d3-9a0c-0305e82c3301", 1L, "코밋툴", "asd",
                    OffsetDateTime.parse("2026-07-30T12:00:00+09:00"));
            given(invitationService.findMyInvitations(5L)).willReturn(List.of(item));

            mockMvc.perform(get("/api/v1/invitations/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data[0].invitationId").value("3f2504e0-4f89-11d3-9a0c-0305e82c3301"))
                    .andExpect(jsonPath("$.data[0].spaceId").value(1))
                    .andExpect(jsonPath("$.data[0].spaceName").value("코밋툴"))
                    .andExpect(jsonPath("$.data[0].inviterNickname").value("asd"));
        }

        @Test
        @DisplayName("받은 초대가 없으면 200과 빈 배열(null 아님)을 반환한다")
        void findMyInvitations_returns200WithEmptyArray() throws Exception {
            given(invitationService.findMyInvitations(5L)).willReturn(List.of());

            mockMvc.perform(get("/api/v1/invitations/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data").isEmpty());
        }
    }

    @Nested
    @DisplayName("MEMBER-03 POST /api/v1/invitations/{invitationId}/accept")
    class AcceptInvitation {

        private static final String INVITATION_ID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

        @Test
        @DisplayName("정상 수락이면 200 SUCCESS와 data=null을 반환한다")
        void acceptInvitation_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(invitationService).acceptInvitation(5L, INVITATION_ID);
        }

        @Test
        @DisplayName("존재하지 않거나 만료됐거나 본인 초대가 아니면 404 INVITATION_NOT_FOUND를 반환한다")
        void acceptInvitation_returns404WhenNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.INVITATION_NOT_FOUND))
                    .when(invitationService).acceptInvitation(5L, INVITATION_ID);

            mockMvc.perform(post("/api/v1/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("이미 멤버면 409 MEMBER_ALREADY_JOINED를 반환한다")
        void acceptInvitation_returns409WhenAlreadyMember() throws Exception {
            doThrow(new CustomException(ErrorCode.MEMBER_ALREADY_JOINED))
                    .when(invitationService).acceptInvitation(5L, INVITATION_ID);

            mockMvc.perform(post("/api/v1/invitations/{invitationId}/accept", INVITATION_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_JOINED"));
        }
    }

    @Nested
    @DisplayName("MEMBER-03 POST /api/v1/invitations/{invitationId}/reject")
    class RejectInvitation {

        private static final String INVITATION_ID = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";

        @Test
        @DisplayName("정상 거절이면 200 SUCCESS, message='초대 거절 성공', data=null을 반환한다")
        void rejectInvitation_returns200() throws Exception {
            mockMvc.perform(post("/api/v1/invitations/{invitationId}/reject", INVITATION_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("초대 거절 성공"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(invitationService).rejectInvitation(5L, INVITATION_ID);
        }

        @Test
        @DisplayName("존재하지 않거나 만료됐거나 본인 초대가 아니면 404 INVITATION_NOT_FOUND를 반환한다")
        void rejectInvitation_returns404WhenNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.INVITATION_NOT_FOUND))
                    .when(invitationService).rejectInvitation(5L, INVITATION_ID);

            mockMvc.perform(post("/api/v1/invitations/{invitationId}/reject", INVITATION_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        }

        @Test
        @DisplayName("잘못된 UUID 형식 path variable이면 서비스에서 INVITATION_NOT_FOUND를 발생시켜 404를 반환한다")
        void rejectInvitation_returns404WhenInvalidUuidFormat() throws Exception {
            String invalidId = "not-a-uuid";
            doThrow(new CustomException(ErrorCode.INVITATION_NOT_FOUND))
                    .when(invitationService).rejectInvitation(5L, invalidId);

            mockMvc.perform(post("/api/v1/invitations/{invitationId}/reject", invalidId))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("INVITATION_NOT_FOUND"));
        }
    }
}