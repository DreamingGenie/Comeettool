package com.ssafy.backend.space.mapper;

import com.ssafy.backend.space.dto.ResponseCreateSpaceDto;
import com.ssafy.backend.space.dto.ResponseSpaceDetailDto;
import com.ssafy.backend.space.dto.ResponseSpaceListDto;
import com.ssafy.backend.space.dto.ResponseSpaceMemberDto;
import com.ssafy.backend.space.dto.ResponseUpdateSpaceDto;
import com.ssafy.backend.member.entity.Member;
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

    public ResponseUpdateSpaceDto toUpdateResponse(Team team) {
        return new ResponseUpdateSpaceDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getColor(),
                team.getProfileImageUrl(),
                team.getOwnerId()
        );
    }

    public ResponseSpaceListDto toListItem(Team team, String myAuthority, long memberCount) {
        return new ResponseSpaceListDto(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getColor(),
                team.getProfileImageUrl(),
                team.getOwnerId(),
                myAuthority,
                memberCount
        );
    }

    public ResponseSpaceMemberDto toMemberDto(Member member) {
        return new ResponseSpaceMemberDto(
                member.getId(),
                member.getUserId(),
                member.getNickname(),
                member.getAuthority().name(),
                member.getTeamRoleId()
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
