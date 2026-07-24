package com.ssafy.backend.auth.mapper;

import com.ssafy.backend.auth.dto.ResponseSignupDto;
import com.ssafy.backend.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * auth 도메인의 Entity ↔ Dto 변환 전담 (User는 user 패키지 소속이라 여기서 매핑을 모은다).
 */
@Component
public class UserMapper {

    public ResponseSignupDto toSignupResponse(User user) {
        return new ResponseSignupDto(user.getId(), user.getEmail(), user.getCreatedAt());
    }
}