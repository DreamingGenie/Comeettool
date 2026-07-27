package com.ssafy.backend.auth.service.impl;

import com.ssafy.backend.auth.dto.RequestLoginDto;
import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.RequestTokenRefreshDto;
import com.ssafy.backend.auth.dto.ResponseLoginDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.dto.ResponseTokenRefreshDto;
import com.ssafy.backend.auth.mapper.UserMapper;
import com.ssafy.backend.auth.service.RefreshTokenService;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.global.jwt.JwtProvider;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AuthServiceImpl 단위 테스트 (AUTH-01 회원가입, AUTH-02 로그인, AUTH-03 토큰 재발급).
 * Repository/Mapper/PasswordEncoder/JwtProvider/RefreshTokenService는 전부 Mock —
 * 실제 DB·BCrypt·JWT 서명 연산 없이 서비스 로직만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl 단위 테스트")
class AuthServiceImplTest {

    private static final String EMAIL = "user@example.com";
    private static final String RAW_PASSWORD = "pass1234!";
    private static final String ENCODED_PASSWORD = "$2a$10$encodedPasswordValue";

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtProvider jwtProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthServiceImpl authService;

    private RequestSignupDto requestSignupDto;

    @BeforeEach
    void setUp() {
        requestSignupDto = new RequestSignupDto(EMAIL, RAW_PASSWORD);
    }

    // =====================================================================
    // AUTH-01: signup() 테스트
    // =====================================================================

    @Nested
    @DisplayName("회원가입 성공")
    class SignupSuccess {

        @Test
        @DisplayName("이메일이_중복되지_않으면_회원가입에_성공하고_응답DTO를_반환한다")
        void 이메일이_중복되지_않으면_회원가입에_성공하고_응답DTO를_반환한다() {
            // given
            ResponseSignupDto expected = new ResponseSignupDto(1L, EMAIL, OffsetDateTime.now());
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(userMapper.toSignupResponse(any(User.class))).willReturn(expected);

            // when
            ResponseSignupDto actual = authService.signup(requestSignupDto);

            // then
            assertThat(actual).isEqualTo(expected);
            verify(userRepository, times(1)).save(any(User.class));
            verify(userMapper, times(1)).toSignupResponse(any(User.class));
        }

        @Test
        @DisplayName("회원가입_시_저장되는_비밀번호는_평문이_아닌_인코딩된_값이다")
        void 회원가입_시_저장되는_비밀번호는_평문이_아닌_인코딩된_값이다() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(userMapper.toSignupResponse(any(User.class)))
                    .willReturn(new ResponseSignupDto(1L, EMAIL, OffsetDateTime.now()));

            // when
            authService.signup(requestSignupDto);

            // then
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPassword())
                    .isEqualTo(ENCODED_PASSWORD)
                    .isNotEqualTo(RAW_PASSWORD);
        }
    }

    @Nested
    @DisplayName("회원가입 실패 - 이메일 중복")
    class SignupFailure {

        @Test
        @DisplayName("이메일이_이미_존재하면_AUTH_EMAIL_DUPLICATED_예외가_발생한다")
        void 이메일이_이미_존재하면_AUTH_EMAIL_DUPLICATED_예외가_발생한다() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(requestSignupDto))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
        }

        @Test
        @DisplayName("이메일_중복_예외_발생시_비밀번호_인코딩과_저장과_매핑은_수행되지_않는다")
        void 이메일_중복_예외_발생시_비밀번호_인코딩과_저장과_매핑은_수행되지_않는다() {
            // given
            given(userRepository.existsByEmail(EMAIL)).willReturn(true);

            // when
            assertThatThrownBy(() -> authService.signup(requestSignupDto))
                    .isInstanceOf(CustomException.class);

            // then
            verify(passwordEncoder, never()).encode(anyString());
            verify(userRepository, never()).save(any(User.class));
            verify(userMapper, never()).toSignupResponse(any(User.class));
        }
    }

    @Nested
    @DisplayName("이메일 대소문자 정규화")
    class EmailCaseNormalization {

        @Test
        @DisplayName("이메일이_대문자를_포함해도_소문자로_정규화해_저장한다")
        void 이메일이_대문자를_포함해도_소문자로_정규화해_저장한다() {
            // given
            String mixedCaseEmail = "User@Example.com";
            String normalizedEmail = "user@example.com";
            RequestSignupDto request = new RequestSignupDto(mixedCaseEmail, RAW_PASSWORD);
            given(userRepository.existsByEmail(normalizedEmail)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(userMapper.toSignupResponse(any(User.class)))
                    .willReturn(new ResponseSignupDto(1L, normalizedEmail, OffsetDateTime.now()));

            // when
            authService.signup(request);

            // then: 중복검사·저장 둘 다 소문자로 정규화된 이메일로 수행돼야 한다
            verify(userRepository).existsByEmail(normalizedEmail);
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo(normalizedEmail);
        }

        @Test
        @DisplayName("대소문자만_다른_이메일도_중복으로_판단해_AUTH_EMAIL_DUPLICATED_예외가_발생한다")
        void 대소문자만_다른_이메일도_중복으로_판단해_AUTH_EMAIL_DUPLICATED_예외가_발생한다() {
            // given: "user@example.com"이 이미 가입돼 있고, "User@Example.com"으로 재가입을 시도
            String mixedCaseEmail = "User@Example.com";
            String normalizedEmail = "user@example.com";
            RequestSignupDto request = new RequestSignupDto(mixedCaseEmail, RAW_PASSWORD);
            given(userRepository.existsByEmail(normalizedEmail)).willReturn(true);

            // when & then
            assertThatThrownBy(() -> authService.signup(request))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_EMAIL_DUPLICATED);
            verify(userRepository, never()).save(any(User.class));
        }
    }

    /**
     * 서비스 계층은 null/blank를 방어하지 않는다 — Bean Validation(@NotBlank 등, RequestSignupDto)이
     * 컨트롤러의 @Valid에서 먼저 걸러낸다는 전제를 검증한다.
     */
    @Nested
    @DisplayName("RequestSignupDto Bean Validation (컨트롤러 진입 전 방어선)")
    class RequestSignupDtoValidation {

        private static ValidatorFactory validatorFactory;
        private static Validator validator;

        @BeforeAll
        static void setUpValidator() {
            validatorFactory = Validation.buildDefaultValidatorFactory();
            validator = validatorFactory.getValidator();
        }

        @AfterAll
        static void closeValidator() {
            validatorFactory.close();
        }

        @Test
        @DisplayName("이메일이_null이면_검증에_실패한다")
        void 이메일이_null이면_검증에_실패한다() {
            RequestSignupDto invalid = new RequestSignupDto(null, RAW_PASSWORD);
            Set<ConstraintViolation<RequestSignupDto>> violations = validator.validate(invalid);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("비밀번호가_빈문자열이면_검증에_실패한다")
        void 비밀번호가_빈문자열이면_검증에_실패한다() {
            RequestSignupDto invalid = new RequestSignupDto(EMAIL, "");
            Set<ConstraintViolation<RequestSignupDto>> violations = validator.validate(invalid);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("비밀번호가_숫자만_포함하면_검증에_실패한다")
        void 비밀번호가_숫자만_포함하면_검증에_실패한다() {
            RequestSignupDto invalid = new RequestSignupDto(EMAIL, "12345678");
            Set<ConstraintViolation<RequestSignupDto>> violations = validator.validate(invalid);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("비밀번호에_특수문자가_없으면_검증에_실패한다")
        void 비밀번호에_특수문자가_없으면_검증에_실패한다() {
            RequestSignupDto invalid = new RequestSignupDto(EMAIL, "pass1234");
            Set<ConstraintViolation<RequestSignupDto>> violations = validator.validate(invalid);
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("유효한_이메일과_비밀번호는_검증을_통과한다")
        void 유효한_이메일과_비밀번호는_검증을_통과한다() {
            RequestSignupDto valid = new RequestSignupDto(EMAIL, RAW_PASSWORD);
            Set<ConstraintViolation<RequestSignupDto>> violations = validator.validate(valid);
            assertThat(violations).isEmpty();
        }
    }

    // =====================================================================
    // AUTH-02: login() 테스트
    // =====================================================================

    @Nested
    @DisplayName("로그인 성공")
    class LoginSuccess {

        private static final String ACCESS_TOKEN = "access.token.value";
        private static final String REFRESH_TOKEN = "refresh.token.value";
        /** 14일(초) — JwtProperties.refreshExpirationSeconds 와 일치해야 한다. */
        private static final long REFRESH_TTL_SECONDS = 14 * 24 * 60 * 60L; // 1_209_600

        private User userWithId;

        @BeforeEach
        void setUp() {
            userWithId = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
            ReflectionTestUtils.setField(userWithId, "id", 1L);
        }

        private void givenLoginStubs() {
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(userWithId));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtProvider.createAccessToken("1")).willReturn(ACCESS_TOKEN);
            given(jwtProvider.createRefreshToken("1")).willReturn(REFRESH_TOKEN);
            given(jwtProvider.getRefreshExpirationSeconds()).willReturn(REFRESH_TTL_SECONDS);
        }

        @Test
        @DisplayName("이메일과_비밀번호가_일치하면_로그인에_성공하고_토큰DTO를_반환한다")
        void 이메일과_비밀번호가_일치하면_로그인에_성공하고_토큰DTO를_반환한다() {
            givenLoginStubs();

            ResponseLoginDto result = authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD));

            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.userId()).isEqualTo("1");
        }

        @Test
        @DisplayName("로그인_성공_시_tokenType은_Bearer다")
        void 로그인_성공_시_tokenType은_Bearer다() {
            givenLoginStubs();

            ResponseLoginDto result = authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD));

            assertThat(result.tokenType()).isEqualTo("Bearer");
        }

        @Test
        @DisplayName("accessToken과_refreshToken은_서로_다른_값이다")
        void accessToken과_refreshToken은_서로_다른_값이다() {
            givenLoginStubs();

            ResponseLoginDto result = authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD));

            assertThat(result.accessToken()).isNotEqualTo(result.refreshToken());
        }

        @Test
        @DisplayName("로그인_성공_시_refreshToken이_Redis에_저장된다")
        void 로그인_성공_시_refreshToken이_Redis에_저장된다() {
            givenLoginStubs();

            authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD));

            verify(refreshTokenService, times(1)).save(eq("1"), eq(REFRESH_TOKEN), eq(REFRESH_TTL_SECONDS));
        }

        @Test
        @DisplayName("Redis_저장_TTL은_정확히_14일_초_단위다")
        void Redis_저장_TTL은_정확히_14일_초_단위다() {
            givenLoginStubs();

            authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD));

            ArgumentCaptor<Long> ttlCaptor = ArgumentCaptor.forClass(Long.class);
            verify(refreshTokenService).save(anyString(), anyString(), ttlCaptor.capture());
            assertThat(ttlCaptor.getValue())
                    .as("TTL은 14일(1,209,600초)이어야 한다")
                    .isEqualTo(1_209_600L);
        }
    }

    @Nested
    @DisplayName("로그인 실패")
    class LoginFailure {

        @Test
        @DisplayName("존재하지_않는_이메일로_로그인하면_AUTH_LOGIN_FAILED_예외가_발생한다")
        void 존재하지_않는_이메일로_로그인하면_AUTH_LOGIN_FAILED_예외가_발생한다() {
            // given
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        }

        @Test
        @DisplayName("비밀번호가_일치하지_않으면_AUTH_LOGIN_FAILED_예외가_발생한다")
        void 비밀번호가_일치하지_않으면_AUTH_LOGIN_FAILED_예외가_발생한다() {
            // given
            User user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.matches("wrongPassword!", ENCODED_PASSWORD)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.login(new RequestLoginDto(EMAIL, "wrongPassword!")))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_LOGIN_FAILED);
        }

        @Test
        @DisplayName("이메일이_없으면_토큰_발급과_Redis_저장은_수행되지_않는다")
        void 이메일이_없으면_토큰_발급과_Redis_저장은_수행되지_않는다() {
            // given
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.empty());

            // when
            assertThatThrownBy(() -> authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(CustomException.class);

            // then
            verify(jwtProvider, never()).createAccessToken(anyString());
            verify(jwtProvider, never()).createRefreshToken(anyString());
            verify(refreshTokenService, never()).save(anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("비밀번호가_틀리면_토큰_발급과_Redis_저장은_수행되지_않는다")
        void 비밀번호가_틀리면_토큰_발급과_Redis_저장은_수행되지_않는다() {
            // given
            User user = User.builder().email(EMAIL).password(ENCODED_PASSWORD).build();
            given(userRepository.findByEmail(EMAIL)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(anyString(), eq(ENCODED_PASSWORD))).willReturn(false);

            // when
            assertThatThrownBy(() -> authService.login(new RequestLoginDto(EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(CustomException.class);

            // then
            verify(jwtProvider, never()).createAccessToken(anyString());
            verify(jwtProvider, never()).createRefreshToken(anyString());
            verify(refreshTokenService, never()).save(anyString(), anyString(), anyLong());
        }
    }

    @Nested
    @DisplayName("로그인 이메일 대소문자 정규화")
    class LoginEmailNormalization {

        @Test
        @DisplayName("대문자_이메일로_로그인해도_소문자로_정규화하여_유저를_조회한다")
        void 대문자_이메일로_로그인해도_소문자로_정규화하여_유저를_조회한다() {
            // given
            String mixedCaseEmail = "User@Example.com";
            String normalizedEmail = "user@example.com";
            User user = User.builder().email(normalizedEmail).password(ENCODED_PASSWORD).build();
            ReflectionTestUtils.setField(user, "id", 1L);

            given(userRepository.findByEmail(normalizedEmail)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(RAW_PASSWORD, ENCODED_PASSWORD)).willReturn(true);
            given(jwtProvider.createAccessToken("1")).willReturn("access.token");
            given(jwtProvider.createRefreshToken("1")).willReturn("refresh.token");
            given(jwtProvider.getRefreshExpirationSeconds()).willReturn(1209600L);

            // when
            authService.login(new RequestLoginDto(mixedCaseEmail, RAW_PASSWORD));

            // then
            verify(userRepository, times(1)).findByEmail(normalizedEmail);
        }
    }

    @Nested
    @DisplayName("RequestLoginDto Bean Validation (컨트롤러 진입 전 방어선)")
    class RequestLoginDtoValidation {

        private static ValidatorFactory validatorFactory;
        private static Validator validator;

        @BeforeAll
        static void setUpValidator() {
            validatorFactory = Validation.buildDefaultValidatorFactory();
            validator = validatorFactory.getValidator();
        }

        @AfterAll
        static void closeValidator() {
            validatorFactory.close();
        }

        @Test
        @DisplayName("이메일이_null이면_검증에_실패한다")
        void 이메일이_null이면_검증에_실패한다() {
            Set<ConstraintViolation<RequestLoginDto>> violations =
                    validator.validate(new RequestLoginDto(null, RAW_PASSWORD));
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("이메일_형식이_아니면_검증에_실패한다")
        void 이메일_형식이_아니면_검증에_실패한다() {
            Set<ConstraintViolation<RequestLoginDto>> violations =
                    validator.validate(new RequestLoginDto("not-an-email", RAW_PASSWORD));
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("비밀번호가_빈문자열이면_검증에_실패한다")
        void 비밀번호가_빈문자열이면_검증에_실패한다() {
            Set<ConstraintViolation<RequestLoginDto>> violations =
                    validator.validate(new RequestLoginDto(EMAIL, ""));
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("유효한_이메일과_비밀번호는_검증을_통과한다")
        void 유효한_이메일과_비밀번호는_검증을_통과한다() {
            Set<ConstraintViolation<RequestLoginDto>> violations =
                    validator.validate(new RequestLoginDto(EMAIL, RAW_PASSWORD));
            assertThat(violations).isEmpty();
        }
    }

    // =====================================================================
    // AUTH-03: refreshAccessToken() 테스트
    // =====================================================================

    @Nested
    @DisplayName("Access Token 재발급 성공")
    class TokenRefreshSuccess {

        private static final String REFRESH_TOKEN = "valid.refresh.token";
        private static final String NEW_ACCESS_TOKEN = "new.access.token";
        private static final String USER_ID = "1";

        private void givenValidRefreshToken() {
            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn(USER_ID);
            given(claims.get("type", String.class)).willReturn("refresh");
            given(jwtProvider.parse(REFRESH_TOKEN)).willReturn(claims);
            given(refreshTokenService.isValid(USER_ID, REFRESH_TOKEN)).willReturn(true);
            given(jwtProvider.createAccessToken(USER_ID)).willReturn(NEW_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("refreshToken이_유효하면_새_accessToken을_발급하고_tokenType은_Bearer다")
        void refreshToken이_유효하면_새_accessToken을_발급하고_tokenType은_Bearer다() {
            // given
            givenValidRefreshToken();

            // when
            ResponseTokenRefreshDto result = authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN));

            // then
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.accessToken()).isEqualTo(NEW_ACCESS_TOKEN);
        }

        @Test
        @DisplayName("재발급_성공해도_refreshToken은_재발급되거나_Redis에_다시_쓰이지_않는다_rotation_없음")
        void 재발급_성공해도_refreshToken은_재발급되거나_Redis에_다시_쓰이지_않는다_rotation_없음() {
            // given
            givenValidRefreshToken();

            // when
            authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN));

            // then: 기존 refreshToken·Redis 값 그대로 유지 — write 자체가 없어야 한다
            verify(jwtProvider, never()).createRefreshToken(anyString());
            verify(refreshTokenService, never()).save(anyString(), anyString(), anyLong());
            verify(refreshTokenService, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("Access Token 재발급 실패")
    class TokenRefreshFailure {

        private static final String REFRESH_TOKEN = "some.refresh.token";
        private static final String USER_ID = "1";

        @Test
        @DisplayName("서명이_유효하지_않으면_AUTH_REFRESH_FAILED_예외가_발생한다")
        void 서명이_유효하지_않으면_AUTH_REFRESH_FAILED_예외가_발생한다() {
            // given
            given(jwtProvider.parse(REFRESH_TOKEN)).willThrow(new SignatureException("invalid signature"));

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_REFRESH_FAILED);
        }

        @Test
        @DisplayName("만료된_refreshToken이면_AUTH_REFRESH_FAILED_예외가_발생한다")
        void 만료된_refreshToken이면_AUTH_REFRESH_FAILED_예외가_발생한다() {
            // given
            given(jwtProvider.parse(REFRESH_TOKEN)).willThrow(new ExpiredJwtException(null, null, "expired"));

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_REFRESH_FAILED);
        }

        @Test
        @DisplayName("type이_access인_토큰으로_요청하면_AUTH_REFRESH_FAILED_예외가_발생한다")
        void type이_access인_토큰으로_요청하면_AUTH_REFRESH_FAILED_예외가_발생한다() {
            // given: access 토큰을 refresh 엔드포인트에 잘못 사용한 경우 — 파싱 자체는 성공한다
            Claims claims = mock(Claims.class);
            given(claims.get("type", String.class)).willReturn("access");
            given(jwtProvider.parse(REFRESH_TOKEN)).willReturn(claims);

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_REFRESH_FAILED);
            // type 검증에서 이미 걸러지므로 Redis 조회까지 갈 필요가 없다
            verify(refreshTokenService, never()).isValid(anyString(), anyString());
        }

        @Test
        @DisplayName("Redis_검증에_실패하면_AUTH_REFRESH_FAILED_예외가_발생한다")
        void Redis_검증에_실패하면_AUTH_REFRESH_FAILED_예외가_발생한다() {
            // given: isValid()가 false를 반환하는 모든 경우(키 없음·값 불일치)를 대표하는 케이스.
            // AuthServiceImpl 입장에서는 두 상황이 isValid() == false로 동일하게 관찰되어 구분할 수 없다.
            Claims claims = mock(Claims.class);
            given(claims.getSubject()).willReturn(USER_ID);
            given(claims.get("type", String.class)).willReturn("refresh");
            given(jwtProvider.parse(REFRESH_TOKEN)).willReturn(claims);
            given(refreshTokenService.isValid(USER_ID, REFRESH_TOKEN)).willReturn(false);

            // when & then
            assertThatThrownBy(() -> authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN)))
                    .isInstanceOf(CustomException.class)
                    .extracting(ex -> ((CustomException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.AUTH_REFRESH_FAILED);
        }

        @Test
        @DisplayName("실패_시_원인에_상관없이_Redis에_어떤_값도_쓰지_않고_새_accessToken도_발급하지_않는다")
        void 실패_시_원인에_상관없이_Redis에_어떤_값도_쓰지_않고_새_accessToken도_발급하지_않는다() {
            // given
            given(jwtProvider.parse(REFRESH_TOKEN)).willThrow(new SignatureException("invalid signature"));

            // when
            assertThatThrownBy(() -> authService.refreshAccessToken(new RequestTokenRefreshDto(REFRESH_TOKEN)))
                    .isInstanceOf(CustomException.class);

            // then
            verify(jwtProvider, never()).createAccessToken(anyString());
            verify(refreshTokenService, never()).save(anyString(), anyString(), anyLong());
            verify(refreshTokenService, never()).delete(anyString());
        }
    }

    @Nested
    @DisplayName("RequestTokenRefreshDto Bean Validation (컨트롤러 진입 전 방어선)")
    class RequestTokenRefreshDtoValidation {

        private static ValidatorFactory validatorFactory;
        private static Validator validator;

        @BeforeAll
        static void setUpValidator() {
            validatorFactory = Validation.buildDefaultValidatorFactory();
            validator = validatorFactory.getValidator();
        }

        @AfterAll
        static void closeValidator() {
            validatorFactory.close();
        }

        @Test
        @DisplayName("refreshToken이_null이면_검증에_실패한다")
        void refreshToken이_null이면_검증에_실패한다() {
            Set<ConstraintViolation<RequestTokenRefreshDto>> violations =
                    validator.validate(new RequestTokenRefreshDto(null));
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("refreshToken이_빈문자열이면_검증에_실패한다")
        void refreshToken이_빈문자열이면_검증에_실패한다() {
            Set<ConstraintViolation<RequestTokenRefreshDto>> violations =
                    validator.validate(new RequestTokenRefreshDto(""));
            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("유효한_refreshToken은_검증을_통과한다")
        void 유효한_refreshToken은_검증을_통과한다() {
            Set<ConstraintViolation<RequestTokenRefreshDto>> violations =
                    validator.validate(new RequestTokenRefreshDto("some.jwt.token"));
            assertThat(violations).isEmpty();
        }
    }
}