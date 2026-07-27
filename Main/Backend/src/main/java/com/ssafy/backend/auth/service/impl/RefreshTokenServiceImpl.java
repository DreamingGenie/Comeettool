package com.ssafy.backend.auth.service.impl;

import com.ssafy.backend.auth.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final String PREFIX = "refresh:";

    private final StringRedisTemplate redisTemplate;

    @Override
    public void save(String userId, String refreshToken, long ttlSeconds) {
        redisTemplate.opsForValue().set(PREFIX + userId, refreshToken, Duration.ofSeconds(ttlSeconds));
    }

    @Override
    public boolean isValid(String userId, String refreshToken) {
        String saved = redisTemplate.opsForValue().get(PREFIX + userId);
        return saved != null && saved.equals(refreshToken);
    }

    @Override
    public void delete(String userId) {
        redisTemplate.delete(PREFIX + userId);
    }
}