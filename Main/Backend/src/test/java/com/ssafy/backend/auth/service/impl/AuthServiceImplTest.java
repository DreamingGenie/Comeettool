package com.ssafy.backend.auth.service.impl;

import com.ssafy.backend.auth.dto.RequestSignupDto;
import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.auth.mapper.UserMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.user.entity.User;
import com.ssafy.backend.user.repository.UserRepository;
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

import java.time.OffsetDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * AuthServiceImpl.signup() 단위 테스트.
 * Repository/Mapper/PasswordEncoder는 전부 Mock — 실제 DB·BCrypt 연산 없이 서비스 로직만 검증한다.
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

    @InjectMocks
    private AuthServiceImpl authService;

    private RequestSignupDto requestSignupDto;

    @BeforeEach
    void setUp() {
        requestSignupDto = new RequestSignupDto(EMAIL, RAW_PASSWORD);
    }

    @Nested
    @DisplayName("회원가입 성공")
    class SignupSuccess {

        @Test
        @DisplayName("이메일이_중복되지_않으면_회원가입에_성공하고_응답DTO를_반환한다")
        void 이메일이_중복되지_않으면_회원가입에_성공하고_응답DTO를_반환한다() {
            // given
            ResponseSignupDto expected = new ResponseSignupDto(1, EMAIL, OffsetDateTime.now());
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
                    .willReturn(new ResponseSignupDto(1, EMAIL, OffsetDateTime.now()));

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
    @DisplayName("이메일 대소문자 처리 (현재 동작 문서화)")
    class EmailCaseSensitivity {

        @Test
        @DisplayName("이메일_대소문자만_다르면_서비스는_별개_이메일로_취급해_중복_검사를_그대로_통과시킨다")
        void 이메일_대소문자만_다르면_서비스는_별개_이메일로_취급해_중복_검사를_그대로_통과시킨다() {
            // given: 대소문자가 다른 이메일 — 서비스는 정규화 없이 받은 문자열 그대로 조회한다
            String mixedCaseEmail = "User@Example.com";
            RequestSignupDto request = new RequestSignupDto(mixedCaseEmail, RAW_PASSWORD);
            given(userRepository.existsByEmail(mixedCaseEmail)).willReturn(false);
            given(passwordEncoder.encode(RAW_PASSWORD)).willReturn(ENCODED_PASSWORD);
            given(userRepository.save(any(User.class))).willAnswer(invocation -> invocation.getArgument(0));
            given(userMapper.toSignupResponse(any(User.class)))
                    .willReturn(new ResponseSignupDto(1, mixedCaseEmail, OffsetDateTime.now()));

            // when & then: 예외 없이 통과 — "user@example.com"이 이미 가입돼 있어도 서비스 계층에서는 걸러내지 못함
            assertThatCode(() -> authService.signup(request)).doesNotThrowAnyException();
            verify(userRepository).existsByEmail(mixedCaseEmail);
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
}