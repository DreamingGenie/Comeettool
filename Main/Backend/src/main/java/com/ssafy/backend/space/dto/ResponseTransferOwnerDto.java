package com.ssafy.backend.space.dto;

/**
 * SPACE-101 소유권 위임 성공 응답.
 * previousOwnerId·newOwnerId는 teams.team_owner_id에 저장하는 users.user_id다.
 */
public record ResponseTransferOwnerDto(
        Long spaceId,
        Long previousOwnerId,
        Long newOwnerId
) {
}
