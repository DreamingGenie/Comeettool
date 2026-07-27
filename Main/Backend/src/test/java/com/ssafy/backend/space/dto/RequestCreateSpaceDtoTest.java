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
    @DisplayName("이름이_null이면_검증에_실패한다")
    void 이름이_null이면_검증에_실패한다() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto(null, null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름이_공백만_있으면_검증에_실패한다")
    void 이름이_공백만_있으면_검증에_실패한다() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("   ", null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름이_100자를_초과하면_검증에_실패한다")
    void 이름이_100자를_초과하면_검증에_실패한다() {
        String tooLong = "가".repeat(101);
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto(tooLong, null, null, null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("이름만_있고_나머지가_null이면_검증을_통과한다")
    void 이름만_있고_나머지가_null이면_검증을_통과한다() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("팀A", null, null, null));
        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("이름_설명_색상_이미지가_모두_유효하면_검증을_통과한다")
    void 모든_필드가_유효하면_검증을_통과한다() {
        Set<ConstraintViolation<RequestCreateSpaceDto>> violations =
                validator.validate(new RequestCreateSpaceDto("팀A", "설명", "#123456", "https://img/x.png"));
        assertThat(violations).isEmpty();
    }
}
