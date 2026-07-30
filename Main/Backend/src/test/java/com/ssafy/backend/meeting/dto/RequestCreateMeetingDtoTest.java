
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

@DisplayName("RequestCreateMeetingDto 검증")
class RequestCreateMeetingDtoTest {

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
    @DisplayName("회의방 이름이 null이면 검증에 실패한다")
    void validation_failsWhenMeetingRoomNameIsNull() {
        Set<ConstraintViolation<RequestCreateMeetingDto>> violations =
                validator.validate(new RequestCreateMeetingDto(null));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("회의방 이름이 공백이면 검증에 실패한다")
    void validation_failsWhenMeetingRoomNameIsBlank() {
        Set<ConstraintViolation<RequestCreateMeetingDto>> violations =
                validator.validate(new RequestCreateMeetingDto("   "));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("회의방 이름이 250자를 초과하면 검증에 실패한다")
    void validation_failsWhenMeetingRoomNameExceedsMaximumLength() {
        Set<ConstraintViolation<RequestCreateMeetingDto>> violations =
                validator.validate(new RequestCreateMeetingDto("가".repeat(251)));

        assertThat(violations).isNotEmpty();
    }

    @Test
    @DisplayName("회의방 이름이 250자이면 검증을 통과한다")
    void validation_passesWhenMeetingRoomNameHasMaximumLength() {
        Set<ConstraintViolation<RequestCreateMeetingDto>> violations =
                validator.validate(new RequestCreateMeetingDto("가".repeat(250)));

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("유효한 회의방 이름이면 검증을 통과한다")
    void validation_passesWhenMeetingRoomNameIsValid() {
        Set<ConstraintViolation<RequestCreateMeetingDto>> violations =
                validator.validate(new RequestCreateMeetingDto("데일리 미팅"));

        assertThat(violations).isEmpty();
    }
}
