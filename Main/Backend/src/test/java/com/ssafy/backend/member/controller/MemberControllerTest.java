package com.ssafy.backend.member.controller;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.member.service.MemberService;
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

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    @Mock
    private MemberService memberService;

    @InjectMocks
    private MemberController memberController;

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
}