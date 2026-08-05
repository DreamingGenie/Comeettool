package com.ssafy.backend.global.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정 (application.yml의 jwt.*).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(
        String privateKeyBase64,
        String publicKeyBase64,
        String privateKeyPath,
        String publicKeyPath,
        String issuer,
        String apiAudience,
        String yjsAudience,
        long accessExpirationSeconds,
        long refreshExpirationSeconds,
        long collaborationExpirationSeconds
) {
}
