package com.ssafy.backend.space.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestCreateSpaceDto Bean Validation (컨트롤러 @Valid 진입 전 방어선).
 */
@DisplayName("RequestCreateSpaceDto 검증")
class RequestCreateSpaceDtoTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @Test
    @DisplayName("이름이 null이면 검증에 실패한다")
    void validation_failsWhenNameNull() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto(null, null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름이 공백만 있으면 검증에 실패한다")
    void validation_failsWhenNameBlank() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("   ", null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름이 100자를 초과하면 검증에 실패한다")
    void validation_failsWhenNameTooLong() {
        String tooLong = "가".repeat(101);
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto(tooLong, null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름만 있고 나머지가 null이면 검증을 통과한다")
    void validation_passesWithNameOnly() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("팀A", null, null, null));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이름·설명·색상·이미지가 모두 유효하면 검증을 통과한다")
    void validation_passesWhenAllFieldsValid() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("팀A", "설명", "#123456", "https://img/x.png"));
        assertThat(violations).isEmpty();
    }
}
