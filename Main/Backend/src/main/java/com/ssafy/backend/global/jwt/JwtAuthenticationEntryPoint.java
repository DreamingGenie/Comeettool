package com.ssafy.backend.global.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 미인증(401) 처리. 필터가 저장한 사유에 따라 만료/무효/누락을 구분해 응답한다.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        String attr = (String) request.getAttribute(JwtAuthenticationFilter.ERROR_ATTRIBUTE);
        ErrorCode errorCode = switch (attr == null ? "" : attr) {
            case "AUTH_TOKEN_EXPIRED" -> ErrorCode.AUTH_TOKEN_EXPIRED;
            case "AUTH_TOKEN_INVALID" -> ErrorCode.AUTH_TOKEN_INVALID;
            default -> ErrorCode.AUTH_UNAUTHORIZED;
        };
        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(errorCode));
    }
}
