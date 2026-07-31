package com.ssafy.backend.member.dto;

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
 * RequestInviteMemberDto Bean Validation (컨트롤러 @Valid 진입 전 방어선).
 */
@DisplayName("RequestInviteMemberDto 검증")
class RequestInviteMemberDtoTest {

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
    @DisplayName("targetUserId가 null이면 검증에 실패한다")
    void validation_failsWhenTargetUserIdNull() {
        Set<ConstraintViolation<RequestInviteMemberDto>> violations =
                validator.validate(new RequestInviteMemberDto(null));
        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("targetUserId가 있으면 검증을 통과한다")
    void validation_passesWhenTargetUserIdPresent() {
        Set<ConstraintViolation<RequestInviteMemberDto>> violations =
                validator.validate(new RequestInviteMemberDto(1L));
        assertThat(violations).isEmpty();
    }
}