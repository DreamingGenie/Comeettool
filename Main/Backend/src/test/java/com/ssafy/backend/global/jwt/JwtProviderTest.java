package com.ssafy.backend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtProvider RS256 발급·검증 단위 테스트 (Spring 컨텍스트 없이 keys/ 로드).
 */
@DisplayName("JWT 발급·검증 (JwtProvider)")
class JwtProviderTest {

    private final JwtProvider provider = new JwtProvider(
            new JwtProperties("keys/jwt_private.pem", "keys/jwt_public.pem", 1800, 1209600));

    @Test
    @DisplayName("access 토큰 발급·검증 라운드트립")
    void accessTokenRoundTrip() {
        String token = provider.createAccessToken("42");
        Claims claims = provider.parse(token);
        assertEquals("42", claims.getSubject());
        assertEquals("access", claims.get("type", String.class));
    }

    @Test
    @DisplayName("refresh 토큰은 jti와 type을 가진다")
    void refreshTokenHasJtiAndType() {
        String token = provider.createRefreshToken("42");
        Claims claims = provider.parse(token);
        assertEquals("refresh", claims.get("type", String.class));
        assertNotNull(claims.getId());
    }

    @Test
    @DisplayName("만료된 토큰은 ExpiredJwtException을 던진다")
    void expiredTokenThrowsException() {
        // 만료폭을 clock skew(60s)보다 크게 두어 확실히 만료 처리되게 함
        JwtProvider expired = new JwtProvider(
                new JwtProperties("keys/jwt_private.pem", "keys/jwt_public.pem", -120, -120));
        String token = expired.createAccessToken("42");
        assertThrows(ExpiredJwtException.class, () -> expired.parse(token));
    }
}
