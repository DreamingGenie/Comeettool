package com.ssafy.backend.user.mapper;

import com.ssafy.backend.user.dto.ResponseMyProfileDto;
import com.ssafy.backend.user.entity.User;
import org.springframework.stereotype.Component;

/**
 * user 도메인의 Entity ↔ Dto 변환 전담.
 */
@Component
public class UserProfileMapper {

    public ResponseMyProfileDto toMyProfileResponse(User user) {
        return new ResponseMyProfileDto(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getPhone(),
                user.getProfileImageUrl(),
                user.getSex(),
                user.getAge(),
                user.getJobFamily(),
                user.getJobRole(),
                user.getDescription(),
                user.getDisplayColor()
        );
    }
}