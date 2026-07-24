package com.ssafy.backend.auth.dto;

import java.time.OffsetDateTime;

// nickname은 가입 시점에 없으므로(온보딩에서 채움) 응답에 포함하지 않는다.
public record ResponseSignupDto(
        Integer userId,
        String email,
        OffsetDateTime createdAt
) {
}