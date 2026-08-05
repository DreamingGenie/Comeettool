package com.ssafy.backend.auth.service;

/**
 * Refresh Token을 Redis에 저장·검증·폐기한다 (auth-jwt-contract §5).
 * 키: refresh:{userId} (사용자당 1세션), TTL = refresh 만료.
 */
public interface RefreshTokenService {

    void save(String userId, String refreshToken, long ttlSeconds);

    boolean isValid(String userId, String refreshToken);

    void delete(String userId);
}