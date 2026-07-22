package com.ssafy.backend.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Refresh Token을 Redis에 저장·검증·폐기한다 (auth-jwt-contract §5).
 * 키: refresh:{userId} (사용자당 1세션), TTL = refresh 만료.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    public void save(String userId, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + userId, refreshToken, Duration.ofSeconds(ttlSeconds));
    }

    public boolean isValid(String userId, String refreshToken) {
        String saved = redisTemplate.opsForValue().get(PREFIX + userId);
        return saved != null && saved.equals(refreshToken);
    }

    public void delete(String userId) {
        redisTemplate.delete(PREFIX + userId);
    }
}
