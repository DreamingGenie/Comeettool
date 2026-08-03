package com.ssafy.backend.member.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.member.dto.RequestAssignTeamRoleDto;
import com.ssafy.backend.member.dto.RequestChangeAuthorityDto;
import com.ssafy.backend.member.dto.RequestCreateTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseAssignTeamRoleDto;
import com.ssafy.backend.member.dto.ResponseChangeAuthorityDto;
import com.ssafy.backend.member.dto.ResponseTeamRoleDto;
import com.ssafy.backend.member.service.MemberService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MemberController 슬라이스 테스트 (standalone MockMvc).
 * MemberService는 Mock, @AuthenticationPrincipal은 SecurityContext + ArgumentResolver로 주입한다.
 * 토큰 자체가 없는 401 케이스는 실제 필터 체인이 필요해 JwtAuthenticationFilterTest(@SpringBootTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MemberController 슬라이스 테스트")
class MemberControllerTest {

    private static final String USER_ID = "1";
    private static final Long SPACE_ID = 10L;
    private static final Long MEMBER_ID = 100L;
    private static final Long TEAM_ROLE_ID = 3L;

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(memberController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
    }

    @Nested
    @DisplayName("MEMBER-04 DELETE /api/v1/spaces/{spaceId}/members/{memberId}")
    class KickMember {

        @Test
        @DisplayName("정상 강퇴이면 200 SUCCESS, message='멤버 강퇴 성공', data=null을 반환한다")
        void kickMember_returns200() throws Exception {
            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/{memberId}", SPACE_ID, MEMBER_ID))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("멤버 강퇴 성공"))
                    .andExpect(jsonPath("$.data").doesNotExist());

            verify(memberService).kickMember(1L, SPACE_ID, MEMBER_ID);
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void kickMember_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(memberService).kickMember(1L, SPACE_ID, MEMBER_ID);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/{memberId}", SPACE_ID, MEMBER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void kickMember_returns403WhenNotOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY))
                    .when(memberService).kickMember(1L, SPACE_ID, MEMBER_ID);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/{memberId}", SPACE_ID, MEMBER_ID))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("존재하지 않거나 다른 스페이스 소속 멤버면 404 SPACE_MEMBER_NOT_FOUND를 반환한다")
        void kickMember_returns404WhenMemberNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND))
                    .when(memberService).kickMember(1L, SPACE_ID, MEMBER_ID);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/{memberId}", SPACE_ID, MEMBER_ID))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_MEMBER_NOT_FOUND"));
        }

        @Test
        @DisplayName("본인을 강퇴 시도하면 409 CANNOT_KICK_SELF를 반환한다")
        void kickMember_returns409WhenKickingSelf() throws Exception {
            doThrow(new CustomException(ErrorCode.CANNOT_KICK_SELF))
                    .when(memberService).kickMember(1L, SPACE_ID, MEMBER_ID);

            mockMvc.perform(delete("/api/v1/spaces/{spaceId}/members/{memberId}", SPACE_ID, MEMBER_ID))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANNOT_KICK_SELF"));
        }
    }

    @Nested
    @DisplayName("MEMBER-07 PATCH /api/v1/spaces/{spaceId}/members/{memberId}/authority")
    class ChangeMemberAuthority {

        @Test
        @DisplayName("정상 변경이면 200 SUCCESS, message='멤버 권한이 변경되었습니다.', data에 변경된 권한을 반환한다")
        void changeMemberAuthority_returns200() throws Exception {
            given(memberService.changeMemberAuthority(
                    eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class)))
                    .willReturn(new ResponseChangeAuthorityDto(MEMBER_ID, "GUEST"));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("GUEST"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("멤버 권한이 변경되었습니다."))
                    .andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
                    .andExpect(jsonPath("$.data.authority").value("GUEST"));

            verify(memberService).changeMemberAuthority(
                    eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));
        }

        @Test
        @DisplayName("authority가 없으면 400 VALIDATION_FAILED를 반환한다")
        void changeMemberAuthority_returns400WhenAuthorityMissing() throws Exception {
            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("authority=OWNER로 요청하면 400 CANNOT_SET_OWNER_AUTHORITY와 위임 안내 메시지를 반환한다")
        void changeMemberAuthority_returns400WhenAuthorityIsOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.CANNOT_SET_OWNER_AUTHORITY))
                    .when(memberService).changeMemberAuthority(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("OWNER"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("CANNOT_SET_OWNER_AUTHORITY"))
                    .andExpect(jsonPath("$.message").value(
                            "오너 권한으로는 변경할 수 없습니다. 위임은 SPACE-12(오너 위임 API)를 이용하세요."));
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void changeMemberAuthority_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(memberService).changeMemberAuthority(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("GUEST"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void changeMemberAuthority_returns403WhenNotOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY))
                    .when(memberService).changeMemberAuthority(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("GUEST"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("존재하지 않거나 다른 스페이스 소속 멤버면 404 SPACE_MEMBER_NOT_FOUND를 반환한다")
        void changeMemberAuthority_returns404WhenMemberNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND))
                    .when(memberService).changeMemberAuthority(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("GUEST"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_MEMBER_NOT_FOUND"));
        }

        @Test
        @DisplayName("대상이 오너 본인이면 409 CANNOT_CHANGE_OWNER_AUTHORITY를 반환한다")
        void changeMemberAuthority_returns409WhenTargetIsOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.CANNOT_CHANGE_OWNER_AUTHORITY))
                    .when(memberService).changeMemberAuthority(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestChangeAuthorityDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/authority", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestChangeAuthorityDto("GUEST"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("CANNOT_CHANGE_OWNER_AUTHORITY"));
        }
    }

    @Nested
    @DisplayName("MEMBER-06 PATCH /api/v1/spaces/{spaceId}/members/{memberId}/team-role")
    class AssignTeamRole {

        @Test
        @DisplayName("정상 배정이면 200 SUCCESS, message='멤버 역할이 배정되었습니다.', data에 teamRoleId·roleName을 반환한다")
        void assignTeamRole_returns200() throws Exception {
            given(memberService.assignTeamRole(
                    eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class)))
                    .willReturn(new ResponseAssignTeamRoleDto(MEMBER_ID, TEAM_ROLE_ID, "프론트엔드"));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestAssignTeamRoleDto(TEAM_ROLE_ID))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("멤버 역할이 배정되었습니다."))
                    .andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
                    .andExpect(jsonPath("$.data.teamRoleId").value(TEAM_ROLE_ID))
                    .andExpect(jsonPath("$.data.roleName").value("프론트엔드"));

            verify(memberService).assignTeamRole(
                    eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class));
        }

        @Test
        @DisplayName("teamRoleId 필드 자체를 안 보내면 해제 요청으로 처리되어 data.teamRoleId·roleName이 null로 응답된다")
        void assignTeamRole_returns200WithNullFieldsWhenTeamRoleIdOmitted() throws Exception {
            given(memberService.assignTeamRole(
                    eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class)))
                    .willReturn(new ResponseAssignTeamRoleDto(MEMBER_ID, null, null));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.memberId").value(MEMBER_ID))
                    .andExpect(jsonPath("$.data.teamRoleId").doesNotExist())
                    .andExpect(jsonPath("$.data.roleName").doesNotExist());

            ArgumentCaptor<RequestAssignTeamRoleDto> captor = ArgumentCaptor.forClass(RequestAssignTeamRoleDto.class);
            verify(memberService).assignTeamRole(eq(1L), eq(SPACE_ID), eq(MEMBER_ID), captor.capture());
            assertThat(captor.getValue().teamRoleId()).isNull();
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void assignTeamRole_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(memberService).assignTeamRole(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestAssignTeamRoleDto(TEAM_ROLE_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void assignTeamRole_returns403WhenNotOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY))
                    .when(memberService).assignTeamRole(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestAssignTeamRoleDto(TEAM_ROLE_ID))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("존재하지 않거나 다른 스페이스 소속 멤버면 404 SPACE_MEMBER_NOT_FOUND를 반환한다")
        void assignTeamRole_returns404WhenMemberNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND))
                    .when(memberService).assignTeamRole(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestAssignTeamRoleDto(TEAM_ROLE_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_MEMBER_NOT_FOUND"));
        }

        @Test
        @DisplayName("해당 스페이스에 없는 teamRoleId면 404 TEAM_ROLE_NOT_FOUND를 반환한다")
        void assignTeamRole_returns404WhenTeamRoleNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.TEAM_ROLE_NOT_FOUND))
                    .when(memberService).assignTeamRole(
                            eq(1L), eq(SPACE_ID), eq(MEMBER_ID), any(RequestAssignTeamRoleDto.class));

            mockMvc.perform(patch("/api/v1/spaces/{spaceId}/members/{memberId}/team-role", SPACE_ID, MEMBER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestAssignTeamRoleDto(TEAM_ROLE_ID))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("TEAM_ROLE_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("MEMBER-14 POST /api/v1/spaces/{spaceId}/members/team-roles")
    class CreateTeamRole {

        @Test
        @DisplayName("정상 생성이면 201 SUCCESS, message='역할이 생성되었습니다.', data에 생성된 역할을 반환한다")
        void createTeamRole_returns201() throws Exception {
            OffsetDateTime createdAt = OffsetDateTime.parse("2026-08-03T10:00:00Z");
            given(memberService.createTeamRole(eq(1L), eq(SPACE_ID), any(RequestCreateTeamRoleDto.class)))
                    .willReturn(new ResponseTeamRoleDto(TEAM_ROLE_ID, "프론트엔드", "#3B82F6", createdAt));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RequestCreateTeamRoleDto("프론트엔드", "#3B82F6"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.message").value("역할이 생성되었습니다."))
                    .andExpect(jsonPath("$.data.teamRoleId").value(TEAM_ROLE_ID))
                    .andExpect(jsonPath("$.data.roleName").value("프론트엔드"))
                    .andExpect(jsonPath("$.data.color").value("#3B82F6"));

            verify(memberService).createTeamRole(eq(1L), eq(SPACE_ID), any(RequestCreateTeamRoleDto.class));
        }

        @Test
        @DisplayName("roleName이 없으면 400 VALIDATION_FAILED를 반환한다")
        void createTeamRole_returns400WhenRoleNameMissing() throws Exception {
            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("roleName이 20자를 초과하면 400 VALIDATION_FAILED를 반환한다")
        void createTeamRole_returns400WhenRoleNameTooLong() throws Exception {
            String tooLong = "가".repeat(21);

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new RequestCreateTeamRoleDto(tooLong, null))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("존재하지 않는 스페이스면 404 SPACE_NOT_FOUND를 반환한다")
        void createTeamRole_returns404WhenSpaceNotFound() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_NOT_FOUND))
                    .when(memberService).createTeamRole(eq(1L), eq(SPACE_ID), any(RequestCreateTeamRoleDto.class));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RequestCreateTeamRoleDto("프론트엔드", "#3B82F6"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SPACE_NOT_FOUND"));
        }

        @Test
        @DisplayName("요청자가 소유자가 아니면 403 SPACE_OWNER_ONLY를 반환한다")
        void createTeamRole_returns403WhenNotOwner() throws Exception {
            doThrow(new CustomException(ErrorCode.SPACE_OWNER_ONLY))
                    .when(memberService).createTeamRole(eq(1L), eq(SPACE_ID), any(RequestCreateTeamRoleDto.class));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RequestCreateTeamRoleDto("프론트엔드", "#3B82F6"))))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("SPACE_OWNER_ONLY"));
        }

        @Test
        @DisplayName("같은 스페이스에 동일한 역할명이 있으면 409 TEAM_ROLE_NAME_DUPLICATED를 반환한다")
        void createTeamRole_returns409WhenNameDuplicated() throws Exception {
            doThrow(new CustomException(ErrorCode.TEAM_ROLE_NAME_DUPLICATED))
                    .when(memberService).createTeamRole(eq(1L), eq(SPACE_ID), any(RequestCreateTeamRoleDto.class));

            mockMvc.perform(post("/api/v1/spaces/{spaceId}/members/team-roles", SPACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    new RequestCreateTeamRoleDto("프론트엔드", "#3B82F6"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("TEAM_ROLE_NAME_DUPLICATED"));
        }
    }
}