package com.ssafy.backend.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RequestUpdateProfileDto Bean Validation 테스트 (AUTH-06, 컨트롤러 진입 전 방어선).
 * 전부 선택 필드라 null은 항상 통과해야 하고, 값이 있을 때만 각 제약이 걸린다.
 */
@DisplayName("RequestUpdateProfileDto Bean Validation")
class RequestUpdateProfileDtoTest {

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

    private static RequestUpdateProfileDto allNull() {
        return new RequestUpdateProfileDto(null, null, null, null, null, null, null, null);
    }

    @Test
    @DisplayName("모든_필드가_null이면_검증을_통과한다")
    void 모든_필드가_null이면_검증을_통과한다() {
        Set<ConstraintViolation<RequestUpdateProfileDto>> violations = validator.validate(allNull());
        assertThat(violations).isEmpty();
    }

    @Nested
    @DisplayName("nickname")
    class Nickname {

        @Test
        @DisplayName("20자는_검증을_통과한다")
        void 스무자는_검증을_통과한다() {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    "가".repeat(20), null, null, null, null, null, null, null);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        @DisplayName("21자는_검증에_실패한다")
        void 스물한자는_검증에_실패한다() {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    "가".repeat(21), null, null, null, null, null, null, null);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("sex")
    class Sex {

        @ParameterizedTest
        @ValueSource(strings = {"M", "F"})
        @DisplayName("M_또는_F는_검증을_통과한다")
        void M_또는_F는_검증을_통과한다(String sex) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, sex, null, null, null, null, null);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"m", "male", "X", ""})
        @DisplayName("M_F가_아니면_검증에_실패한다")
        void M_F가_아니면_검증에_실패한다(String sex) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, sex, null, null, null, null, null);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("phone")
    class Phone {

        @ParameterizedTest
        @ValueSource(strings = {"010-1234-5678", "000-0000-0000", "070-9999-9999"})
        @DisplayName("000-0000-0000_형식은_검증을_통과한다")
        void 형식이_맞으면_검증을_통과한다(String phone) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, phone, null, null, null, null, null, null);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"01012345678", "010-123-5678", "010-1234-567", "010.1234.5678", "not-a-phone"})
        @DisplayName("형식에_맞지_않으면_검증에_실패한다")
        void 형식에_맞지_않으면_검증에_실패한다(String phone) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, phone, null, null, null, null, null, null);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("age")
    class Age {

        @ParameterizedTest
        @ValueSource(ints = {10, 20, 30, 40, 50, 60})
        @DisplayName("10_단위_연령대는_검증을_통과한다")
        void 십_단위_연령대는_검증을_통과한다(int age) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, age, null, null, null, null);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 5, 15, 25, 65, -10})
        @DisplayName("10_단위가_아니거나_범위_밖이면_검증에_실패한다")
        void 십_단위가_아니거나_범위_밖이면_검증에_실패한다(int age) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, age, null, null, null, null);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("userDescription")
    class UserDescription {

        @Test
        @DisplayName("255자는_검증을_통과한다")
        void 이백오십오자는_검증을_통과한다() {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, null, null, null, "가".repeat(255), null);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @Test
        @DisplayName("256자는_검증에_실패한다")
        void 이백오십육자는_검증에_실패한다() {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, null, null, null, "가".repeat(256), null);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("userColor")
    class UserColor {

        @ParameterizedTest
        @ValueSource(strings = {"#000000", "#FFFFFF", "#3b82f6", "#AbCdEf"})
        @DisplayName("RRGGBB_hex_형식은_검증을_통과한다")
        void RRGGBB_hex_형식은_검증을_통과한다(String color) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, null, null, null, null, color);
            assertThat(validator.validate(dto)).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"blue", "000000", "#fff", "#GGGGGG", "#12345"})
        @DisplayName("hex_형식이_아니면_검증에_실패한다")
        void hex_형식이_아니면_검증에_실패한다(String color) {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, null, null, null, null, color);
            assertThat(validator.validate(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("jobFamily, jobRole")
    class UnconstrainedFields {

        @Test
        @DisplayName("특별한_제약이_없는_필드는_어떤_값이든_검증을_통과한다")
        void 특별한_제약이_없는_필드는_어떤_값이든_검증을_통과한다() {
            RequestUpdateProfileDto dto = new RequestUpdateProfileDto(
                    null, null, null, null, "아무 직군이나", "아무 직무나", null, null);
            assertThat(validator.validate(dto)).isEmpty();
        }
    }
}