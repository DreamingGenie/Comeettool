package com.ssafy.backend.global.jwt;

import com.ssafy.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ⚠️ 개발 전용(local 프로파일). 로그인 구현(A2) 전, 협업 서버 검증 테스트용으로 유효 access 토큰을 발급한다.
 * prod 프로파일에는 등록되지 않는다. 로그인 구현 후 제거 예정.
 */
@Profile("local")
@RestController
@RequestMapping("/api/v1/dev")
@RequiredArgsConstructor
public class DevTokenController {

    private final JwtProvider jwtProvider;

    @PostMapping("/token")
    public ApiResponse<Map<String, Object>> issueToken(@RequestParam(defaultValue = "1") String userId) {
        String accessToken = jwtProvider.createAccessToken(userId);
        return ApiResponse.success(
                "개발용 액세스 토큰 발급",
                Map.of("tokenType", "Bearer", "userId", userId, "accessToken", accessToken)
        );
    }
}
