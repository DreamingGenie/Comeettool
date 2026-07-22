package com.ssafy.backend.global.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.stereotype.Component;

import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Date;
import java.util.UUID;

/**
 * JWT(RS256) 발급·검증. 개인키로 서명, 공개키로 검증.
 */
@Component
public class JwtProvider {

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final long accessExpMs;
    private final long refreshExpMs;

    public JwtProvider(JwtProperties props) {
        this.privateKey = RsaKeyUtil.loadPrivateKey(props.privateKeyPath());
        this.publicKey = RsaKeyUtil.loadPublicKey(props.publicKeyPath());
        this.accessExpMs = props.accessExpirationSeconds() * 1000;
        this.refreshExpMs = props.refreshExpirationSeconds() * 1000;
    }

    public String createAccessToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + accessExpMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    public String createRefreshToken(String userId) {
        Date now = new Date();
        return Jwts.builder()
                .subject(userId)
                .claim("type", "refresh")
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + refreshExpMs))
                .signWith(privateKey, Jwts.SIG.RS256)
                .compact();
    }

    /** 서명·만료 검증 후 Claims 반환. 실패 시 io.jsonwebtoken 예외를 던진다. */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(publicKey)
                .clockSkewSeconds(60)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpMs / 1000;
    }
}
