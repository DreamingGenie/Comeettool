package com.ssafy.backend.user.controller;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
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

import java.util.Collections;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 단위(standalone MockMvc) 테스트 (AUTH-05).
 * Spring 컨텍스트를 띄우지 않고 컨트롤러 하나만 MockMvc에 등록한다 — SecurityConfig·DB·Redis 전부 불필요.
 * (@WebMvcTest는 앱의 실제 SecurityConfig까지 함께 로드해 JwtProvider 등 연쇄적인 빈이 필요해져 이 방식을 택함)
 * standalone 모드엔 시큐리티 필터 체인이 없어 SecurityMockMvcRequestPostProcessors.authentication()이 먹지 않으므로,
 * SecurityContextHolder에 직접 Authentication을 심어 @AuthenticationPrincipal을 채운다.
 * UserService는 Mock — 요청/응답 매핑·principal(userId) 처리·예외→상태코드 변환만 검증한다.
 * 토큰 자체가 없는 401 케이스는 실제 필터 체인이 필요해 JwtAuthenticationFilterTest(@SpringBootTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    private static final String USER_ID = "1";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    @Nested
    @DisplayName("GET /api/v1/users/me 성공")
    class GetMyProfileSuccess {

        @Test
        @DisplayName("인증된_사용자가_요청하면_200과_프로필_데이터를_그대로_반환한다")
        void 인증된_사용자가_요청하면_200과_프로필_데이터를_그대로_반환한다() throws Exception {
            // given
            ResponseMyProfileDto response = new ResponseMyProfileDto(
                    1L, "user1@test.com", "김인송", "010-1234-5678",
                    "https://cdn.example.com/profile.jpg", "M", 20,
                    "소프트웨어 개발", "Frontend Developer", "안녕하세요!", "#000000"
            );
            given(userService.findMyProfile(1L)).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.email").value("user1@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("김인송"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.profileImage").value("https://cdn.example.com/profile.jpg"))
                    .andExpect(jsonPath("$.data.sex").value("M"))
                    .andExpect(jsonPath("$.data.age").value(20))
                    .andExpect(jsonPath("$.data.jobFamily").value("소프트웨어 개발"))
                    .andExpect(jsonPath("$.data.jobRole").value("Frontend Developer"))
                    .andExpect(jsonPath("$.data.userDescription").value("안녕하세요!"))
                    .andExpect(jsonPath("$.data.userColor").value("#000000"));
        }

        @Test
        @DisplayName("nullable_필드는_JSON에서도_null로_내려온다")
        void nullable_필드는_JSON에서도_null로_내려온다() throws Exception {
            // given: 온보딩 전이라 email·userColor 외엔 전부 null
            ResponseMyProfileDto response = new ResponseMyProfileDto(
                    1L, "user1@test.com", null, null, null, null, null, null, null, null, "#000000"
            );
            given(userService.findMyProfile(1L)).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").doesNotExist())
                    .andExpect(jsonPath("$.data.sex").doesNotExist())
                    .andExpect(jsonPath("$.data.age").doesNotExist());
        }

        @Test
        @DisplayName("인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다")
        void 인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다() throws Exception {
            // given
            given(userService.findMyProfile(42L)).willReturn(
                    new ResponseMyProfileDto(42L, "a@b.com", null, null, null, null, null, null, null, null, "#000000")
            );
            authenticateAs("42");

            // when
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk());

            // then: "42"(String) → 42L(Long)로 정확히 변환되어 서비스에 전달됐는지 확인
            verify(userService).findMyProfile(42L);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me 실패")
    class GetMyProfileFailure {

        @Test
        @DisplayName("서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다")
        void 서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다() throws Exception {
            // given
            given(userService.findMyProfile(1L)).willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }
}