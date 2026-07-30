import { teamCreateColors } from '../constants/teamCreateColors'

const authorityLabel = authority => {
  const normalized = String(authority || '').toUpperCase()
  if (normalized === 'OWNER') return 'Owner'
  if (normalized === 'GUEST') return 'Guest'
  if (normalized === 'MEMBER') return 'Member'
  return authority || 'Member'
}

const initials = value => {
  const name = String(value || '').trim()
  if (!name) return 'TS'

  const words = name.split(/\s+/)
  if (words.length > 1) {
    return words
      .slice(0, 2)
      .map(word => word[0])
      .join('')
      .toUpperCase()
  }

  return name.slice(0, 2).toUpperCase()
}

export const toWorkspaceViewModel = space => ({
  id: String(space?.spaceId ?? ''),
  badge: initials(space?.teamName),
  name: space?.teamName || '',
  role: authorityLabel(space?.myAuthority || 'OWNER'),
  members: Number(space?.memberCount ?? 1),
  memberCount: Number(space?.memberCount ?? 1),
  description: space?.teamDescription || '',
  color: space?.teamColor || '',
  profileImage: space?.teamProfileImage || '',
  ownerId: space?.ownerId ?? null
})

export const toDashboardViewModel = spaces => ({
  workspaces: Array.isArray(spaces)
    ? spaces.map(toWorkspaceViewModel)
    : []
})

export const toTeamViewModel = (space, currentUserId) => {
  const members = Array.isArray(space?.members) ? space.members : []
  const currentMember = members.find(
    member => String(member.userId) === String(currentUserId)
  )
  const memberViewModels = members.map(member => {
    const name = member.nickname || `사용자 ${member.userId}`
    return {
      id: String(member.memberId ?? member.userId ?? ''),
      userId: member.userId ?? null,
      name,
      avatarText: initials(name),
      authority: String(member.authority || 'MEMBER').toUpperCase(),
      authorityLabel: authorityLabel(member.authority),
      teamRoleId: member.teamRoleId ?? null
    }
  })

  return {
    ...toWorkspaceViewModel({
      ...space,
      memberCount: members.length,
      myAuthority: currentMember?.authority
    }),
    eyebrow: 'TEAM SPACE',
    memberCount: members.length,
    members: memberViewModels,
    colorOptions: teamCreateColors,
    defaultMemberRoles: ['Member', 'Guest']
  }
}

export const toMemberRowsViewModel = space =>
  (Array.isArray(space?.members) ? space.members : []).map(member => {
    const name = member.nickname || `사용자 ${member.userId}`
    return [
      initials(name),
      name,
      `사용자 #${member.userId}`,
      authorityLabel(member.authority),
      'Active',
      '현재 참여 중'
    ]
  })

export const toCreateSpaceRequest = workspace => ({
  teamName: workspace?.name || '',
  teamDescription: workspace?.description || '',
  teamColor: workspace?.color || '',
  teamProfileImage: workspace?.profileImage || null
})
