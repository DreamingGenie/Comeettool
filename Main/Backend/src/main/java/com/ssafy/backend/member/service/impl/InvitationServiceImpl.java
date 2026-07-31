package com.ssafy.backend.member.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.dto.InvitationData;
import com.ssafy.backend.member.dto.RequestInviteMemberDto;
import com.ssafy.backend.member.dto.ResponseInviteMemberDto;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.service.InvitationService;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import com.ssafy.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * MEMBER-02 멤버 초대. 초대 상태는 Redis 키의 존재 여부로만 판단한다(별도 상태 필드 없음).
 * 수락/거절 시 즉시 DEL, 만료는 TTL(1일)에 위임(다음 작업 범위).
 */
@Service
@RequiredArgsConstructor
public class InvitationServiceImpl implements InvitationService {

    private static final String INVITATION_KEY_PREFIX = "invitation:";
    private static final String LOOKUP_KEY_PREFIX = "invitation:lookup:";
    private static final Duration TTL = Duration.ofDays(1);

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseInviteMemberDto inviteMember(Long inviterId, Long spaceId, RequestInviteMemberDto request) {
        Team team = teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        // 초대는 스페이스 소유자만 가능(SPACE-11/101과 동일한 Owner 전용 정책).
        if (!team.getOwnerId().equals(inviterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Long targetUserId = request.targetUserId();
        if (!userRepository.existsById(targetUserId)) {
            throw new CustomException(ErrorCode.USER_NOT_FOUND);
        }

        if (memberRepository.existsByTeamIdAndUserId(spaceId, targetUserId)) {
            throw new CustomException(ErrorCode.MEMBER_ALREADY_JOINED);
        }

        String invitationId = UUID.randomUUID().toString();
        String lookupKey = lookupKey(spaceId, targetUserId);

        // SETNX(원자적 확인+선점)로 TOCTOU 없이 "이미 대기 중인 초대" 여부를 판단한다.
        Boolean lookupSet = redisTemplate.opsForValue().setIfAbsent(lookupKey, invitationId, TTL);
        if (Boolean.FALSE.equals(lookupSet)) {
            throw new CustomException(ErrorCode.INVITATION_ALREADY_PENDING);
        }

        InvitationData data = new InvitationData(spaceId, inviterId, targetUserId, OffsetDateTime.now());
        redisTemplate.opsForValue().set(invitationKey(invitationId), serialize(data), TTL);

        return new ResponseInviteMemberDto(invitationId);
    }

    private String invitationKey(String invitationId) {
        return INVITATION_KEY_PREFIX + invitationId;
    }

    private String lookupKey(Long spaceId, Long targetUserId) {
        return LOOKUP_KEY_PREFIX + spaceId + ":" + targetUserId;
    }

    private String serialize(InvitationData data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("초대 데이터 직렬화에 실패했습니다.", e);
        }
    }
}