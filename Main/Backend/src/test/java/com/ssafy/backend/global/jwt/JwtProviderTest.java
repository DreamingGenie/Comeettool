package com.ssafy.backend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JwtProvider RS256 발급·검증 단위 테스트 (Spring 컨텍스트 없이 keys/ 로드).
 */
@DisplayName("JWT 발급·검증 (JwtProvider)")
class JwtProviderTest {

    private final JwtProvider provider = new JwtProvider(
            new JwtProperties("classpath:keys/jwt_private.pem", "classpath:keys/jwt_public.pem", 1800, 1209600));

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
                new JwtProperties("classpath:keys/jwt_private.pem", "classpath:keys/jwt_public.pem", -120, -120));
        String token = expired.createAccessToken("42");
        assertThrows(ExpiredJwtException.class, () -> expired.parse(token));
    }

    // =====================================================================
    // 클레임 상세 검증
    // =====================================================================

    @Nested
    @DisplayName("Access Token 클레임 검증")
    class AccessTokenClaims {

        @Test
        @DisplayName("exp는_iat로부터_정확히_30분(1800초)_뒤다")
        void exp는_iat로부터_정확히_30분_뒤다() {
            Claims claims = provider.parse(provider.createAccessToken("99"));
            long diffSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
            assertThat(diffSeconds).isEqualTo(1800L);
        }
    }

    @Nested
    @DisplayName("Refresh Token 클레임 검증")
    class RefreshTokenClaims {

        @Test
        @DisplayName("sub는_발급_시_전달한_userId와_일치한다")
        void sub는_발급_시_전달한_userId와_일치한다() {
            Claims claims = provider.parse(provider.createRefreshToken("99"));
            assertThat(claims.getSubject()).isEqualTo("99");
        }

        @Test
        @DisplayName("exp는_iat로부터_정확히_14일(1209600초)_뒤다")
        void exp는_iat로부터_정확히_14일_뒤다() {
            Claims claims = provider.parse(provider.createRefreshToken("99"));
            long diffSeconds = (claims.getExpiration().getTime() - claims.getIssuedAt().getTime()) / 1000;
            assertThat(diffSeconds).isEqualTo(1_209_600L);
        }
    }

    @Nested
    @DisplayName("Access Token과 Refresh Token 구분")
    class TokenDistinction {

        @Test
        @DisplayName("동일_userId로_발급한_accessToken과_refreshToken은_서로_다른_값이다")
        void 동일_userId로_발급한_accessToken과_refreshToken은_서로_다른_값이다() {
            String accessToken = provider.createAccessToken("42");
            String refreshToken = provider.createRefreshToken("42");
            assertThat(accessToken).isNotEqualTo(refreshToken);
        }
    }
}
