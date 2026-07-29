package com.ssafy.backend.user.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.exception.GlobalExceptionHandler;
import com.ssafy.backend.user.dto.RequestChangePasswordDto;
import com.ssafy.backend.user.dto.RequestUpdateProfileDto;
import com.ssafy.backend.user.dto.ResponseChangePasswordDto;
import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.dto.ResponseUserSearchDto;
import com.ssafy.backend.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UserController 단위(standalone MockMvc) 테스트 (AUTH-05 findMyProfile, AUTH-06 modifyMyProfile, AUTH-07 changePassword,
 * AUTH-08 withdraw, AUTH-10 findUserList). Spring 컨텍스트를 띄우지 않고 컨트롤러 하나만 MockMvc에 등록한다 — SecurityConfig·DB·Redis 전부 불필요.
 * (@WebMvcTest는 앱의 실제 SecurityConfig까지 함께 로드해 JwtProvider 등 연쇄적인 빈이 필요해져 이 방식을 택함) standalone 모드엔 시큐리티 필터 체인이 없어
 * SecurityMockMvcRequestPostProcessors.authentication()이 먹지 않으므로, SecurityContextHolder에 직접 Authentication을 심어
 * @AuthenticationPrincipal을 채운다. UserService는 Mock — 요청/응답 매핑·principal(userId) 처리·예외→상태코드 변환만 검증한다. 토큰 자체가 없는 401 케이스는
 * 실제 필터 체인이 필요해 JwtAuthenticationFilterTest(@SpringBootTest)에서 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserController 단위 테스트")
class UserControllerTest {

    private static final String USER_ID = "1";

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticateAs(String userId) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList()));
    }

    @Nested
    @DisplayName("GET /api/v1/users/me 성공")
    class GetMyProfileSuccess {

        @Test
        @DisplayName("인증된_사용자가_요청하면_200과_프로필_데이터를_그대로_반환한다")
        void 인증된_사용자가_요청하면_200과_프로필_데이터를_그대로_반환한다() throws Exception {
            // given
            ResponseMyProfileDto response = new ResponseMyProfileDto(
                    1L, "user1@test.com", "김인송", "010-1234-5678",
                    "https://cdn.example.com/profile.jpg", "M", 20,
                    "소프트웨어 개발", "Frontend Developer", "안녕하세요!", "#000000"
            );
            given(userService.findMyProfile(1L)).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.userId").value(1))
                    .andExpect(jsonPath("$.data.email").value("user1@test.com"))
                    .andExpect(jsonPath("$.data.nickname").value("김인송"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.profileImage").value("https://cdn.example.com/profile.jpg"))
                    .andExpect(jsonPath("$.data.sex").value("M"))
                    .andExpect(jsonPath("$.data.age").value(20))
                    .andExpect(jsonPath("$.data.jobFamily").value("소프트웨어 개발"))
                    .andExpect(jsonPath("$.data.jobRole").value("Frontend Developer"))
                    .andExpect(jsonPath("$.data.userDescription").value("안녕하세요!"))
                    .andExpect(jsonPath("$.data.userColor").value("#000000"));
        }

        @Test
        @DisplayName("nullable_필드는_JSON에서도_null로_내려온다")
        void nullable_필드는_JSON에서도_null로_내려온다() throws Exception {
            // given: 온보딩 전이라 email·userColor 외엔 전부 null
            ResponseMyProfileDto response = new ResponseMyProfileDto(
                    1L, "user1@test.com", null, null, null, null, null, null, null, null, "#000000"
            );
            given(userService.findMyProfile(1L)).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").doesNotExist())
                    .andExpect(jsonPath("$.data.sex").doesNotExist())
                    .andExpect(jsonPath("$.data.age").doesNotExist());
        }

        @Test
        @DisplayName("인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다")
        void 인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다() throws Exception {
            // given
            given(userService.findMyProfile(42L)).willReturn(
                    new ResponseMyProfileDto(42L, "a@b.com", null, null, null, null, null, null, null, null, "#000000")
            );
            authenticateAs("42");

            // when
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isOk());

            // then: "42"(String) → 42L(Long)로 정확히 변환되어 서비스에 전달됐는지 확인
            verify(userService).findMyProfile(42L);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users/me 실패")
    class GetMyProfileFailure {

        @Test
        @DisplayName("서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다")
        void 서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다() throws Exception {
            // given
            given(userService.findMyProfile(1L)).willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(get("/api/v1/users/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me 성공")
    class ModifyMyProfileSuccess {

        @Test
        @DisplayName("인증된_사용자가_수정을_요청하면_200과_수정된_프로필을_반환한다")
        void 인증된_사용자가_수정을_요청하면_200과_수정된_프로필을_반환한다() throws Exception {
            // given
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "새닉네임", "010-1234-5678", "M", 20,
                    "소프트웨어 개발", "Frontend Developer", "안녕하세요!", "#3B82F6"
            );
            ResponseMyProfileDto response = new ResponseMyProfileDto(
                    1L, "user1@test.com", "새닉네임", "010-1234-5678",
                    "https://cdn.example.com/profile.jpg", "M", 20,
                    "소프트웨어 개발", "Frontend Developer", "안녕하세요!", "#3B82F6"
            );
            given(userService.modifyMyProfile(eq(1L), any(RequestUpdateProfileDto.class))).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.nickname").value("새닉네임"))
                    .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                    .andExpect(jsonPath("$.data.userColor").value("#3B82F6"));
        }

        @Test
        @DisplayName("빈_body를_보내도_검증을_통과하고_서비스에는_전부_null인_요청이_전달된다")
        void 빈_body를_보내도_검증을_통과하고_서비스에는_전부_null인_요청이_전달된다() throws Exception {
            // given: 필드를 아예 안 보낸 경우 — 실제 미변경 로직은 서비스 계층(UserServiceImplTest)에서 검증됨.
            // 여기선 컨트롤러가 이걸 400으로 거부하지 않고 그대로 통과시키는지만 확인한다.
            ResponseMyProfileDto unchanged = new ResponseMyProfileDto(
                    1L, "user1@test.com", "기존닉네임", "010-0000-0000",
                    null, "F", 30, "기존직군", "기존직무", "기존소개", "#000000"
            );
            given(userService.modifyMyProfile(eq(1L), any(RequestUpdateProfileDto.class))).willReturn(unchanged);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.nickname").value("기존닉네임"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me 검증 실패")
    class ModifyMyProfileValidationFailure {

        // 검증 규칙 자체(경계값 등)의 상세 커버리지는 RequestUpdateProfileDtoTest가 전담한다.
        // 여기선 검증 실패가 실제로 400 VALIDATION_FAILED로 배선되는지 대표 케이스 하나로만 확인한다.
        @Test
        @DisplayName("검증에_실패하면_400_VALIDATION_FAILED를_반환한다")
        void 검증에_실패하면_400_VALIDATION_FAILED를_반환한다() throws Exception {
            // given: 21자 (max=20 초과)
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "가".repeat(21), null, null, null, null, null, null, null
            );
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me 실패")
    class ModifyMyProfileFailure {

        @Test
        @DisplayName("서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다")
        void 서비스에서_USER_NOT_FOUND_예외가_발생하면_404와_에러코드를_그대로_응답한다() throws Exception {
            // given
            RequestUpdateProfileDto request = new RequestUpdateProfileDto(
                    "닉네임", null, null, null, null, null, null, null
            );
            given(userService.modifyMyProfile(eq(1L), any(RequestUpdateProfileDto.class)))
                    .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password 성공")
    class ChangePasswordSuccess {

        @Test
        @DisplayName("유효한_요청이면_200과_새_토큰을_반환한다")
        void 유효한_요청이면_200과_새_토큰을_반환한다() throws Exception {
            // given
            RequestChangePasswordDto request = new RequestChangePasswordDto("OldPass1!", "NewPass1!");
            ResponseChangePasswordDto response = new ResponseChangePasswordDto(
                    "Bearer", "new.access.token", "new.refresh.token"
            );
            given(userService.changePassword(eq(1L), any(RequestChangePasswordDto.class))).willReturn(response);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.accessToken").value("new.access.token"))
                    .andExpect(jsonPath("$.data.refreshToken").value("new.refresh.token"));
        }

        @Test
        @DisplayName("principal의_userId가_Long으로_변환되어_서비스에_전달된다")
        void principal의_userId가_Long으로_변환되어_서비스에_전달된다() throws Exception {
            // given
            RequestChangePasswordDto request = new RequestChangePasswordDto("OldPass1!", "NewPass1!");
            given(userService.changePassword(eq(1L), any(RequestChangePasswordDto.class)))
                    .willReturn(new ResponseChangePasswordDto("Bearer", "a", "r"));
            authenticateAs(USER_ID);

            // when
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk());

            // then
            verify(userService).changePassword(eq(1L), any(RequestChangePasswordDto.class));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password 검증 실패")
    class ChangePasswordValidationFailure {

        @Test
        @DisplayName("currentPassword가_blank이면_400_VALIDATION_FAILED를_반환한다")
        void currentPassword가_blank이면_400_VALIDATION_FAILED를_반환한다() throws Exception {
            // given: currentPassword 빈 문자열
            RequestChangePasswordDto request = new RequestChangePasswordDto("", "NewPass1!");
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }

        @Test
        @DisplayName("newPassword가_정책_위반이면_400_VALIDATION_FAILED를_반환한다")
        void newPassword가_정책_위반이면_400_VALIDATION_FAILED를_반환한다() throws Exception {
            // given: 특수문자 없음 — 정책 위반
            RequestChangePasswordDto request = new RequestChangePasswordDto("OldPass1!", "NoSpecial1");
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/users/me/password 실패")
    class ChangePasswordFailure {

        @Test
        @DisplayName("현재_비밀번호_불일치_시_401_PASSWORD_MISMATCH를_반환한다")
        void 현재_비밀번호_불일치_시_401_PASSWORD_MISMATCH를_반환한다() throws Exception {
            // given
            RequestChangePasswordDto request = new RequestChangePasswordDto("WrongPass1!", "NewPass1!");
            given(userService.changePassword(eq(1L), any(RequestChangePasswordDto.class)))
                    .willThrow(new CustomException(ErrorCode.PASSWORD_MISMATCH));
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        }

        @Test
        @DisplayName("서비스에서_USER_NOT_FOUND가_발생하면_404를_반환한다")
        void 서비스에서_USER_NOT_FOUND가_발생하면_404를_반환한다() throws Exception {
            // given
            RequestChangePasswordDto request = new RequestChangePasswordDto("OldPass1!", "NewPass1!");
            given(userService.changePassword(eq(1L), any(RequestChangePasswordDto.class)))
                    .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(patch("/api/v1/users/me/password")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me 성공")
    class WithdrawSuccess {

        @Test
        @DisplayName("인증된_사용자가_요청하면_200과_SUCCESS를_반환한다")
        void 인증된_사용자가_요청하면_200과_SUCCESS를_반환한다() throws Exception {
            // given: withdraw()는 void라 별도 스텁 없이도 기본적으로 아무 예외 없이 통과한다.
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(delete("/api/v1/users/me"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        @Test
        @DisplayName("인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다")
        void 인증_principal의_문자열_userId를_Long으로_변환해_서비스에_전달한다() throws Exception {
            // given
            authenticateAs("42");

            // when
            mockMvc.perform(delete("/api/v1/users/me"))
                    .andExpect(status().isOk());

            // then
            verify(userService).withdraw(42L);
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/users/me 실패")
    class WithdrawFailure {

        @Test
        @DisplayName("이미_탈퇴한_계정이면_409_ALREADY_DELETED_USER를_반환한다")
        void 이미_탈퇴한_계정이면_409_ALREADY_DELETED_USER를_반환한다() throws Exception {
            // given
            org.mockito.Mockito.doThrow(new CustomException(ErrorCode.ALREADY_DELETED_USER))
                    .when(userService).withdraw(1L);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(delete("/api/v1/users/me"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("ALREADY_DELETED_USER"));
        }

        @Test
        @DisplayName("서비스에서_USER_NOT_FOUND가_발생하면_404를_반환한다")
        void 서비스에서_USER_NOT_FOUND가_발생하면_404를_반환한다() throws Exception {
            // given
            org.mockito.Mockito.doThrow(new CustomException(ErrorCode.USER_NOT_FOUND))
                    .when(userService).withdraw(1L);
            authenticateAs(USER_ID);

            // when & then
            mockMvc.perform(delete("/api/v1/users/me"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users 검색 성공")
    class SearchUsersSuccess {

        @Test
        @DisplayName("query로_검색하면_200과_검색_결과_목록을_반환한다")
        void query로_검색하면_200과_검색_결과_목록을_반환한다() throws Exception {
            // given
            authenticateAs(USER_ID);
            List<ResponseUserSearchDto> response = List.of(
                    new ResponseUserSearchDto(2L, "asd", "asd@naver.com", "https://cdn.example.com/1.jpg"),
                    new ResponseUserSearchDto(3L, "asd1", "qwer@naver.com", null)
            );
            given(userService.findUserList(1L, "asd")).willReturn(response);

            // when & then
            mockMvc.perform(get("/api/v1/users").param("query", "asd"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].userId").value(2))
                    .andExpect(jsonPath("$.data[0].nickname").value("asd"))
                    .andExpect(jsonPath("$.data[0].email").value("asd@naver.com"))
                    .andExpect(jsonPath("$.data[0].profileImage").value("https://cdn.example.com/1.jpg"))
                    .andExpect(jsonPath("$.data[1].profileImage").doesNotExist());
        }

        @Test
        @DisplayName("검색_결과가_없으면_빈_배열과_200을_반환한다")
        void 검색_결과가_없으면_빈_배열과_200을_반환한다() throws Exception {
            // given
            authenticateAs(USER_ID);
            given(userService.findUserList(1L, "없음")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/users").param("query", "없음"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/users 검색 - query 파라미터 처리")
    class SearchUsersQueryParamHandling {

        @Test
        @DisplayName("query_파라미터가_아예_없어도_400이_아니라_빈_문자열로_서비스를_호출하고_200을_반환한다")
        void query_파라미터가_아예_없어도_400이_아니라_빈_문자열로_서비스를_호출하고_200을_반환한다() throws Exception {
            // given: 컨트롤러에서 defaultValue = ""로 처리 — 파라미터 자체를 안 보내도 400/500이 나지 않아야 한다.
            authenticateAs(USER_ID);
            given(userService.findUserList(1L, "")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/users"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
            verify(userService).findUserList(1L, "");
        }

        @Test
        @DisplayName("query가_빈_문자열이어도_200과_빈_배열을_반환한다")
        void query가_빈_문자열이어도_200과_빈_배열을_반환한다() throws Exception {
            // given
            authenticateAs(USER_ID);
            given(userService.findUserList(1L, "")).willReturn(List.of());

            // when & then
            mockMvc.perform(get("/api/v1/users").param("query", ""))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(0));
        }
    }
}