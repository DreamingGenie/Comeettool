package com.ssafy.backend.space.mapper;

import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseSpaceMemberDto;
import com.ssafy.backend.space.entity.Member;
import com.ssafy.backend.space.entity.Team;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * space 도메인 Entity ↔ Dto 변환 전담.
 */
@Component
public class SpaceMapper {

    public ResponseCreateSpaceDto toCreateResponse(Team team) {
        return new ResponseCreateSpaceDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getColor(),
                team.getProfileImageUrl(),
                team.getOwnerId(),
                team.getCreatedAt()
        );
    }

    public ResponseSpaceListDto toListItem(Team team, String myRole, long memberCount) {
        return new ResponseSpaceListDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getColor(),
                team.getProfileImageUrl(),
                team.getOwnerId(),
                myRole,
                memberCount
        );
    }

    public ResponseSpaceMemberDto toMemberDto(Member member) {
        return new ResponseSpaceMemberDto(
                member.getId(),
                member.getUserId(),
                member.getNickname(),
                member.getRole().name(),
                member.getAuthority().name()
        );
    }

    public ResponseSpaceDetailDto toDetailResponse(Team team, List<Member> members) {
        List<ResponseSpaceMemberDto> memberDtos = members.stream()
                .map(this::toMemberDto)
                .toList();
        return new ResponseSpaceDetailDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getColor(),
                team.getProfileImageUrl(),
                team.getOwnerId(),
                team.getCreatedAt(),
                memberDtos
        );
    }
}
