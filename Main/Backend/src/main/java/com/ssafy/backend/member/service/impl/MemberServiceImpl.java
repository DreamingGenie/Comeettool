package com.ssafy.backend.member.service.impl;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.Member;
import com.ssafy.backend.member.repository.MemberRepository;
import com.ssafy.backend.member.service.MemberService;
import com.ssafy.backend.space.entity.Team;
import com.ssafy.backend.space.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private final TeamRepository teamRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional
    public void kickMember(Long requesterId, Long spaceId, Long memberId) {
        Team team = teamRepository.findByIdAndIsDeletedFalse(spaceId)
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_NOT_FOUND));

        if (!team.getOwnerId().equals(requesterId)) {
            throw new CustomException(ErrorCode.SPACE_OWNER_ONLY);
        }

        Member member = memberRepository.findById(memberId)
                .filter(m -> m.getTeamId().equals(spaceId))
                .orElseThrow(() -> new CustomException(ErrorCode.SPACE_MEMBER_NOT_FOUND));

        if (member.getUserId().equals(requesterId)) {
            throw new CustomException(ErrorCode.CANNOT_KICK_SELF);
        }

        memberRepository.delete(member);
    }
}