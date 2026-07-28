package com.ssafy.backend.user.dto;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// age는 연령대(10 단위)만 허용 — Bean Validation 표준 어노테이션엔 "값 집합 하나" 제약이 없어 커스텀으로 만듦.
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AgeGroupValidator.class)
public @interface ValidAgeGroup {

    String message() default "age는 10~60 사이의 10 단위 값이어야 합니다.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}