package com.ssafy.backend.user.dto;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Set;

public class AgeGroupValidator implements ConstraintValidator<ValidAgeGroup, Integer> {

    private static final Set<Integer> ALLOWED_AGE_GROUPS = Set.of(10, 20, 30, 40, 50, 60);

    @Override
    public boolean isValid(Integer value, ConstraintValidatorContext context) {
        // 필드 자체를 안 보낸 경우(null)는 미변경 — @NotNull이 아니라 여기선 통과시킨다.
        return value == null || ALLOWED_AGE_GROUPS.contains(value);
    }
}