package com.ssafy.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 회원가입은 email·password만 받는다 — nickname 등 프로필 항목은 온보딩 단계에서 별도로 채운다.
public record RequestSignupDto(

        @NotBlank
        @Email
        @Size(max = 100)
        String email,

        // 8~20자, 영문+숫자+특수문자(!@#$%^&*()_+-=) 모두 포함 (auth.md AUTH-01보다 강화된 정책)
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=-])[A-Za-z\\d!@#$%^&*()_+=-]{8,20}$",
                message = "8~20자, 영문+숫자+특수문자를 모두 포함해야 합니다."
        )

        String password
) {
}