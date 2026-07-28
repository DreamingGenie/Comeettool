package com.ssafy.backend.global.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 (application.yml의 jwt.*).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKeyPath,
        String publicKeyPath,
        String issuer,
        String apiAudience,
        String yjsAudience,
        long accessExpirationSeconds,
        long refreshExpirationSeconds
) {
}
