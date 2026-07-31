package com.ssafy.backend.meeting.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * MEET-06 호스트 양도 요청 DTO 검증 테스트.
 * 양도 대상 Participant ID의 필수 입력 규칙을 검증한다.
 */
@DisplayName("RequestTransferHostDto 검증")
class RequestTransferHostDtoTest {

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
    @DisplayName("새 호스트 Participant ID가 null이면 검증에 실패한다")
    void validation_failsWhenParticipantIdIsNull() {
        Set<ConstraintViolation<RequestTransferHostDto>> violations =
                validator.validate(new RequestTransferHostDto(null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("새 호스트 Participant ID가 0 이하이면 검증에 실패한다")
    void validation_failsWhenParticipantIdIsNotPositive() {
        Set<ConstraintViolation<RequestTransferHostDto>> violations =
                validator.validate(new RequestTransferHostDto(0L));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("새 호스트 Participant ID가 있으면 검증을 통과한다")
    void validation_passesWhenParticipantIdExists() {
        Set<ConstraintViolation<RequestTransferHostDto>> violations =
                validator.validate(new RequestTransferHostDto(30L));

        assertThat(violations).isEmpty();
    }
}
