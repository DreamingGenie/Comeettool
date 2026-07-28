package com.ssafy.backend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT(RS256) 발급·검증. 개인키로 서명, 공개키로 검증.
 */
@Component
public class JwtProvider {

    /**
     * JWKS·토큰 헤더 매칭용 키 식별자. 키 교체 시 함께 변경한다.
     */
    public static final String KEY_ID = "commonpjt-rsa";

    private final PrivateKey privateKey;
    private final RSAPublicKey publicKey;
    private final String issuer;
    private final String apiAudience;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtProvider(JwtProperties props) {
        this.privateKey = RsaKeyUtil.loadPrivateKey(props.privateKeyPath());
        this.publicKey = (RSAPublicKey) RsaKeyUtil.loadPublicKey(props.publicKeyPath());
        this.issuer = props.issuer();
        this.apiAudience = props.apiAudience();
        this.accessExpMs = props.accessExpirationSeconds() * 1000;
        this.refreshExpMs = props.refreshExpirationSeconds() * 1000;
    }

    public String createAccessToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .subject(userId)
                .issuer(issuer)
                .audience().add(apiAudience).and()
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String createRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .header().keyId(KEY_ID).and()
                .subject(userId)
                .issuer(issuer)
                .audience().add(apiAudience).and()
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /**
     * 서명·만료 검증 후 Claims 반환. 실패 시 io.jsonwebtoken 예외를 던진다.
     */
    public Claims parse(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(publicKey)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        if (!issuer.equals(claims.getIssuer())) {
            throw new JwtException("Invalid issuer");
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(apiAudience)) {
            throw new JwtException("Invalid audience");
        }
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new JwtException("Missing subject");
        }
        if (claims.get("type", String.class) == null) {
            throw new JwtException("Missing token type");
        }

        return claims;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return KEY_ID;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpMs / 1000;
    }
}
