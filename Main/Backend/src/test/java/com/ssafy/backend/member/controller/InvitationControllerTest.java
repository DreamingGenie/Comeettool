package com.ssafy.backend.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.service.InvitationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * InvitationController 슬라이스 테스트 (standalone MockMvc).
 * InvitationService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 * 토큰 자체가 없는 401 케이스는 실제 필터 체인이 필요해 JwtAuthenticationFilterTest(@SpringBootTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationController 슬라이스 테스트")
class InvitationControllerTest {

    private static final String USER_ID = "1";
    private static final Long TEAM_ID = 10L;
    private static final Long TARGET_USER_ID = 2L;

    @Mock
    private InvitationService invitationService;

    @InjectMocks
    private InvitationController invitationController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(invitationController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Nested
    @DisplayName("MEMBER-02 POST /api/v1/spaces/{spaceId}/invitations")
    class InviteMember {

        @Test
        @DisplayName("유효한 요청이면 201과 invitationId를 반환한다")
        void inviteMember_returns201WithInvitationId() throws Exception {
            RequestInviteMemberDto request = new RequestInviteMemberDto(TARGET_USER_ID);
            String invitationId = "3f2504e0-4f89-11d3-9a0c-0305e82c3301";
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willReturn(new ResponseInviteMemberDto(invitationId));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.invitationId").value(invitationId));
        }

        @Test
        @DisplayName("targetUserId가 없으면 400 VALIDATION_FAILED를 반환한다")
        void inviteMember_returns400WhenTargetMissing() throws Exception {
            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("존재하지 않거나 삭제된 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void inviteMember_returns404WhenSpaceAbsent() throws Exception {
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestInviteMemberDto(TARGET_USER_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void inviteMember_returns403ForNonOwner() throws Exception {
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestInviteMemberDto(TARGET_USER_ID))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("대상 유저가 존재하지 않으면 404 USER_NOT_FOUND를 반환한다")
        void inviteMember_returns404WhenTargetUserNotFound() throws Exception {
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestInviteMemberDto(TARGET_USER_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        @DisplayName("이미 멤버인 유저면 409 MEMBER_ALREADY_JOINED를 반환한다")
        void inviteMember_returns409WhenAlreadyMember() throws Exception {
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willThrow(new CustomException(ErrorCode.MEMBER_ALREADY_JOINED));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestInviteMemberDto(TARGET_USER_ID))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("MEMBER_ALREADY_JOINED"));
        }

        @Test
        @DisplayName("이미 대기 중인 초대가 있으면 409 INVITATION_ALREADY_PENDING을 반환한다")
        void inviteMember_returns409WhenInvitationAlreadyPending() throws Exception {
            given(invitationService.inviteMember(eq(1L), eq(TEAM_ID), any(RequestInviteMemberDto.class)))
                    .willThrow(new CustomException(ErrorCode.INVITATION_ALREADY_PENDING));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/invitations", TEAM_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestInviteMemberDto(TARGET_USER_ID))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("INVITATION_ALREADY_PENDING"));
        }
    }
}