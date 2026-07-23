package com.ssafy.backend.global.jwt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 인증 필터 + SecurityConfig 통합 테스트 (실제 시큐리티 체인).
 * 로컬 PostgreSQL·keys/ 필요 (@SpringBootTest 컨텍스트).
 * (@AutoConfigureMockMvc 대신 MockMvcBuilders로 직접 구성 — Boot 4 패키지 이동 회피)
 */
@SpringBootTest
class JwtAuthenticationFilterTest {

    // 핸들러가 없는 보호 경로 → 인증 통과 시 404(NOT_FOUND)
    private static final String PROTECTED = "/api/v1/spaces";

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
    void 공개_엔드포인트는_토큰없이_접근가능() throws Exception {
        mockMvc.perform(get("/.well-known/jwks.json"))
                .andExpect(status().isOk());
    }

    @Test
    void 보호경로_토큰없음_401_UNAUTHORIZED() throws Exception {
        mockMvc.perform(get(PROTECTED))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_UNAUTHORIZED")));
    }

    @Test
    void 보호경로_무효토큰_401_INVALID() throws Exception {
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_TOKEN_INVALID")));
    }

    @Test
    void 보호경로_만료토큰_401_EXPIRED() throws Exception {
        JwtProvider expired = new JwtProvider(
                new JwtProperties("keys/jwt_private.pem", "keys/jwt_public.pem", -120, -120));
        String token = expired.createAccessToken("42");
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string(containsString("AUTH_TOKEN_EXPIRED")));
    }

    @Test
    void 보호경로_유효토큰_인증통과_404() throws Exception {
        String token = jwtProvider.createAccessToken("42");
        mockMvc.perform(get(PROTECTED).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
