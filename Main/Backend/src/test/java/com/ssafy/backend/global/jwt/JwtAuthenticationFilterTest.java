package com.ssafy.backend.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 필터 + SecurityConfig 통합 테스트 (실제 시큐리티 체인).
 * 로컬 PostgreSQL·keys/ 필요 (@SpringBootTest 컨텍스트).
 * (@AutoConfigureMockMvc 대신 MockMvcBuilders로 직접 구성 — Boot 4 패키지 이동 회피)
 */
@SpringBootTest
@DisplayName("인증 필터 통합 (JwtAuthenticationFilter)")
class JwtAuthenticationFilterTest {

    // 핸들러가 없는 보호 경로 → 인증 통과 시 404(NOT_FOUND).
    // 실제 핸들러(/api/v1/spaces 등)와 겹치지 않도록 존재하지 않는 경로를 쓴다.
    private static final String PROTECTED = "/api/v1/__no_handler__";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtProvider jwtProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    @DisplayName("공개 엔드포인트는 토큰 없이 접근 가능")
    void publicEndpointAccessibleWithoutToken() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("보호 경로 - 토큰 없음 → 401 AUTH_UNAUTHORIZED")
    void protectedNoTokenReturns401Unauthorized() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_UNAUTHORIZED")));
    }

    @Test
    @DisplayName("AUTH-04 로그아웃 - 토큰 없음 → 401 AUTH_UNAUTHORIZED")
    void logoutWithoutTokenReturns401Unauthorized() throws Exception {
        // /logout은 SecurityConfig의 permitAll 목록에 없어 인증이 필요하다.
        // 만료·위변조 토큰에 대한 401 처리는 필터 공통 로직이라 위 PROTECTED 경로 테스트들로 이미 검증됨.
        mockMvc.perform(post("/api/v1/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_UNAUTHORIZED")));
    }

    @Test
    @DisplayName("AUTH-05 내 프로필 조회 - 토큰 없음 → 401 AUTH_UNAUTHORIZED")
    void getMyProfileWithoutTokenReturns401Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_UNAUTHORIZED")));
    }

    @Test
    @DisplayName("보호 경로 - 무효 토큰 → 401 AUTH_TOKEN_INVALID")
    void protectedInvalidTokenReturns401Invalid() throws Exception {
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_TOKEN_INVALID")));
    }

    @Test
    @DisplayName("보호 경로 - 만료 토큰 → 401 AUTH_TOKEN_EXPIRED")
    void protectedExpiredTokenReturns401Expired() throws Exception {
        JwtProvider expired = new JwtProvider(
                new JwtProperties("classpath:keys/jwt_private.pem", "classpath:keys/jwt_public.pem", -120, -120));
        String token = expired.createAccessToken("42");
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_TOKEN_EXPIRED")));
    }

    @Test
    @DisplayName("보호 경로 - 유효 토큰 → 인증 통과(404)")
    void protectedValidTokenPassesAuth() throws Exception {
        String token = jwtProvider.createAccessToken("42");
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUTH-04 로그아웃 - 유효 토큰 → 200 SUCCESS")
    void logoutWithValidTokenReturns200Success() throws Exception {
        // /logout은 실제 핸들러가 있어서(다른 PROTECTED 경로와 달리) 인증 통과 시 200까지 확인 가능하다.
        String token = jwtProvider.createAccessToken("42");
        mockMvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUCCESS")));
    }
}
