package com.ssafy.backend.document.authorization;

import com.ssafy.backend.global.exception.CustomException;
import com.ssafy.backend.global.exception.ErrorCode;
import com.ssafy.backend.member.entity.MemberAuthority;
import com.ssafy.backend.member.repository.MemberRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DocumentAuthorizationService {

    private final MemberRepository memberRepository;

    public Optional<MemberAuthority> findAuthority(Long teamId, Long userId) {
        return memberRepository.findByTeamIdAndUserId(teamId, userId)
                .map(member -> member.getAuthority());
    }

    public MemberAuthority requireTeamMember(Long teamId, Long userId) {
        return findAuthority(teamId, userId)
                .orElseThrow(() -> new CustomException(ErrorCode.DOCUMENT_ACCESS_DENIED));
    }

    public void requireCreatePermission(Long teamId, Long userId) {
        MemberAuthority authority = requireTeamMember(teamId, userId);
        if (authority == MemberAuthority.GUEST) {
            throw new CustomException(ErrorCode.DOCUMENT_CREATE_FORBIDDEN);
        }
    }

    public void requireDeletePermission(Long teamId, Long userId) {
        MemberAuthority authority = requireTeamMember(teamId, userId);
        if (authority != MemberAuthority.OWNER) {
            throw new CustomException(ErrorCode.DOCUMENT_DELETE_FORBIDDEN);
        }
    }

    public Optional<CollaborationPermission> findCollaborationPermission(Long teamId, Long userId) {
        return findAuthority(teamId, userId).map(this::toCollaborationPermission);
    }

    public CollaborationPermission requireCollaborationPermission(Long teamId, Long userId) {
        return toCollaborationPermission(requireTeamMember(teamId, userId));
    }

    private CollaborationPermission toCollaborationPermission(MemberAuthority authority) {
        return authority == MemberAuthority.GUEST
                ? CollaborationPermission.READ
                : CollaborationPermission.WRITE;
    }
}
