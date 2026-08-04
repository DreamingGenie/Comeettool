import { boardMockDatabase } from './boardMockDatabase'
import { cloneMockValue, mockResponse } from '../../../shared/api/mockResponse'

const createId = (prefix) =>
  `${prefix}-${globalThis.crypto?.randomUUID?.() || Date.now().toString(36)}`

const findMockTeam = (teamId) =>
  boardMockDatabase.teams.find((team) => String(team.id) === String(teamId)) ||
  boardMockDatabase.teams[0]

const getMockTeamMembers = (teamId) => {
  const team = findMockTeam(teamId)
  const members =
    boardMockDatabase.inviteMembers[team?.id] ||
    boardMockDatabase.inviteMembers[boardMockDatabase.teams[0]?.id] ||
    []

  return members.map((member) => ({
    memberId: member.id,
    userId: member.userId ?? member.id,
    nickname: member.name,
    authority: member.role,
    teamRoleId: member.teamRoleId ?? null
  }))
}

const teamRolesByTeam = new Map()

const getMockTeamRoles = (teamId) => {
  const key = String(findMockTeam(teamId)?.id ?? teamId)
  if (!teamRolesByTeam.has(key)) {
    teamRolesByTeam.set(key, [
      { teamRoleId: 1, roleName: 'Frontend', color: '#6277d4' },
      { teamRoleId: 2, roleName: 'Backend', color: '#6bb797' }
    ])
  }
  return teamRolesByTeam.get(key)
}

const findMockMember = (teamId, memberId) =>
  getMockTeamMembers(teamId).find((member) => String(member.memberId) === String(memberId))

export const boardMockApi = {
  getDashboard: (search = '') => {
    const keyword = String(search || '')
      .trim()
      .toLowerCase()
    const workspaces = keyword
      ? boardMockDatabase.workspaces.filter((workspace) =>
          workspace.name.toLowerCase().includes(keyword)
        )
      : boardMockDatabase.workspaces
    return mockResponse({ workspaces })
  },
  getTeam: (teamId) => {
    const team = findMockTeam(teamId)
    return mockResponse({
      ...team,
      ownerId:
        getMockTeamMembers(teamId).find((member) => member.authority === 'OWNER')?.userId ?? null,
      members: getMockTeamMembers(teamId)
    })
  },
  getMembers: (teamId) =>
    mockResponse(
      getMockTeamMembers(teamId).map((member) => ({
        ...member,
        avatarText: String(member.nickname || '')
          .slice(0, 2)
          .toUpperCase(),
        name: member.nickname,
        identifier: `user#${member.userId}`,
        authorityLabel:
          member.authority === 'OWNER'
            ? 'Owner'
            : member.authority === 'GUEST'
              ? 'Guest'
              : 'Member',
        status: 'Active',
        activity: '현재 참여 중'
      }))
    ),
  getTeamRoles: (teamId) => mockResponse(getMockTeamRoles(teamId)),
  getActiveMeeting: () => mockResponse(boardMockDatabase.activeMeetings[0] || null),
  getMeetings: (teamId) =>
    mockResponse(
      boardMockDatabase.activeMeetings.filter(
        (meeting) => !meeting.teamId || String(meeting.teamId) === String(teamId)
      )
    ),
  getEvents: (teamId, year, month) => {
    const source =
      boardMockDatabase.calendars[teamId] ||
      boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]
    return mockResponse({
      year,
      month,
      events: source.year === year && source.month === month ? source.events : []
    })
  },
  getArchive: (_teamId, section) => {
    const rows = boardMockDatabase.archives[section] || []
    const stats = boardMockDatabase.archiveStats[section] || null
    return mockResponse({
      rows,
      stats: stats ? { ...stats, total: rows.length } : null
    })
  },
  getMeetingRoom: (meetingId) =>
    mockResponse(
      boardMockDatabase.meetingRooms[meetingId] || Object.values(boardMockDatabase.meetingRooms)[0]
    ),
  joinMeeting: (meetingId) =>
    mockResponse({
      meetingId,
      role: 'MEMBER',
      isHost: true,
      token: `mock-livekit-token-${meetingId}`,
      url: 'ws://127.0.0.1:7880'
    }),
  leaveMeeting: () => mockResponse({ isKick: false }),
  endMeeting: (meetingId) => {
    const meetingIndex = boardMockDatabase.activeMeetings.findIndex(
      (meeting) => String(meeting.id) === String(meetingId)
    )
    if (meetingIndex >= 0) boardMockDatabase.activeMeetings.splice(meetingIndex, 1)
    return mockResponse(null)
  },
  transferMeetingHost: (meetingId, nextHostParticipantId) =>
    mockResponse({
      meetingId,
      previousHostId: null,
      nextHostId: nextHostParticipantId
    }),
  getParticipants: (meetingId) =>
    mockResponse(
      (
        boardMockDatabase.meetingRooms[meetingId] ||
        Object.values(boardMockDatabase.meetingRooms)[0]
      )?.participants || []
    ),
  getMessages: (meetingId) =>
    mockResponse(
      (
        boardMockDatabase.meetingRooms[meetingId] ||
        Object.values(boardMockDatabase.meetingRooms)[0]
      )?.chatMessages || []
    ),
  getInviteMembers: (teamId) =>
    mockResponse(
      boardMockDatabase.inviteMembers[teamId] ||
        boardMockDatabase.inviteMembers[boardMockDatabase.teams[0]?.id] ||
        []
    ),
  updateTeam: (teamId, data) => {
    const team =
      boardMockDatabase.teams.find((item) => String(item.id) === String(teamId)) ||
      boardMockDatabase.teams[0]
    Object.assign(team, data)
    const workspace = boardMockDatabase.workspaces.find(
      (item) => String(item.id) === String(teamId)
    )
    if (workspace) Object.assign(workspace, data)
    return mockResponse(workspace || team)
  },
  reorderWorkspaces: (spaceOrder) => {
    const normalizedOrder = spaceOrder.map(String)
    const workspaceMap = new Map(
      boardMockDatabase.workspaces.map((workspace) => [String(workspace.id), workspace])
    )
    const reordered = normalizedOrder.map((spaceId) => workspaceMap.get(spaceId)).filter(Boolean)
    const remaining = boardMockDatabase.workspaces.filter(
      (workspace) => !normalizedOrder.includes(String(workspace.id))
    )
    boardMockDatabase.workspaces.splice(
      0,
      boardMockDatabase.workspaces.length,
      ...reordered,
      ...remaining
    )
    return mockResponse({ workspaces: boardMockDatabase.workspaces })
  },
  inviteMember: (teamId, targetUserId) => {
    const targetTeamId = boardMockDatabase.inviteMembers[teamId]
      ? teamId
      : boardMockDatabase.teams[0]?.id
    const member = {
      id: createId('invite'),
      name: `사용자 ${targetUserId}`,
      userId: targetUserId,
      avatarText: String(targetUserId).slice(0, 2).toUpperCase(),
      role: 'MEMBER'
    }
    boardMockDatabase.inviteMembers[targetTeamId].push(member)
    return mockResponse(member)
  },
  kickMember: (teamId, memberId) => {
    const targetTeamId = boardMockDatabase.inviteMembers[teamId]
      ? teamId
      : boardMockDatabase.teams[0]?.id
    const members = boardMockDatabase.inviteMembers[targetTeamId] || []
    const index = members.findIndex((member) => String(member.id) === String(memberId))
    if (index >= 0) members.splice(index, 1)
    return mockResponse(null)
  },
  changeMemberAuthority: (teamId, memberId, authority) => {
    const member = findMockMember(teamId, memberId)
    if (member) member.authority = authority
    const source = boardMockDatabase.inviteMembers[findMockTeam(teamId)?.id] || []
    const storedMember = source.find((item) => String(item.id) === String(memberId))
    if (storedMember) storedMember.role = authority
    return mockResponse(member)
  },
  assignTeamRole: (teamId, memberId, teamRoleId) => {
    const source = boardMockDatabase.inviteMembers[findMockTeam(teamId)?.id] || []
    const member = source.find((item) => String(item.id) === String(memberId))
    if (member) member.teamRoleId = teamRoleId
    return mockResponse(member)
  },
  createTeamRole: (teamId, data) => {
    const roles = getMockTeamRoles(teamId)
    const role = { teamRoleId: createId('role'), ...data }
    roles.push(role)
    return mockResponse(role)
  },
  updateTeamRole: (teamId, teamRoleId, data) => {
    const role = getMockTeamRoles(teamId).find(
      (item) => String(item.teamRoleId) === String(teamRoleId)
    )
    if (role) Object.assign(role, data)
    return mockResponse(role)
  },
  deleteTeamRole: (teamId, teamRoleId) => {
    const roles = getMockTeamRoles(teamId)
    const index = roles.findIndex((role) => String(role.teamRoleId) === String(teamRoleId))
    if (index >= 0) roles.splice(index, 1)
    return mockResponse(null)
  },
  createWorkspace: (data) => {
    const workspace = {
      id: createId('team'),
      badge: data.name?.slice(0, 2).toUpperCase() || 'TM',
      role: 'Owner',
      members: 1,
      ...data
    }
    boardMockDatabase.workspaces.push(workspace)
    boardMockDatabase.teams.push({
      ...boardMockDatabase.teams[0],
      id: workspace.id,
      badge: workspace.badge,
      eyebrow: `${workspace.name.toUpperCase()} TEAM`,
      name: workspace.name,
      description: workspace.description || '',
      memberCount: 1,
      color: workspace.color
    })
    boardMockDatabase.calendars[workspace.id] = {
      ...cloneMockValue(boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]),
      events: []
    }
    boardMockDatabase.inviteMembers[workspace.id] = []
    boardMockDatabase.membersByTeam[workspace.id] = [
      ['ME', '나', 'me@committool.io', 'Admin', 'Active', 'Last active just now']
    ]
    return mockResponse(workspace)
  },
  leaveWorkspace: (teamId) => {
    const workspaceIndex = boardMockDatabase.workspaces.findIndex(
      (workspace) => String(workspace.id) === String(teamId)
    )
    if (workspaceIndex >= 0) {
      boardMockDatabase.workspaces.splice(workspaceIndex, 1)
    }
    return mockResponse(null)
  },
  deleteWorkspace: (teamId) => {
    const workspaceIndex = boardMockDatabase.workspaces.findIndex(
      (workspace) => String(workspace.id) === String(teamId)
    )
    const teamIndex = boardMockDatabase.teams.findIndex(
      (team) => String(team.id) === String(teamId)
    )

    if (workspaceIndex >= 0) boardMockDatabase.workspaces.splice(workspaceIndex, 1)
    if (teamIndex >= 0) boardMockDatabase.teams.splice(teamIndex, 1)
    delete boardMockDatabase.membersByTeam[teamId]
    delete boardMockDatabase.calendars[teamId]
    delete boardMockDatabase.inviteMembers[teamId]
    return mockResponse(null)
  },
  transferWorkspaceOwnership: (teamId, newOwnerUserId) => {
    const team = findMockTeam(teamId)
    const members =
      boardMockDatabase.inviteMembers[team?.id] ||
      boardMockDatabase.inviteMembers[boardMockDatabase.teams[0]?.id] ||
      []
    const previousOwner = members.find((member) => member.role === 'OWNER')
    const nextOwner = members.find((member) => String(member.id) === String(newOwnerUserId))

    if (previousOwner) previousOwner.role = 'MEMBER'
    if (nextOwner) nextOwner.role = 'OWNER'
    if (team) {
      team.ownerId = nextOwner?.id ?? null
      team.role = 'Member'
    }
    const workspace = boardMockDatabase.workspaces.find(
      (item) => String(item.id) === String(teamId)
    )
    if (workspace) workspace.role = 'Member'

    return mockResponse({
      spaceId: teamId,
      previousOwnerId: previousOwner?.id ?? null,
      newOwnerId: nextOwner?.id ?? null
    })
  },
  createMeeting: (data) => {
    const meeting = {
      id: createId('meeting'),
      teamId: data.spaceId,
      title: data.meetingRoomName || data.title || data.name,
      roomTitle: data.meetingRoomName || data.roomTitle || data.title || data.name,
      status: 'LIVE',
      description: data.description || '새 회의',
      date: data.date || '오늘',
      time: data.time || '지금',
      agendaTime: data.agendaTime || '지금',
      participantCount: 1,
      avatars: ['나'],
      ...data
    }
    boardMockDatabase.activeMeetings.unshift(meeting)
    boardMockDatabase.meetingRooms[meeting.id] = {
      totalParticipants: 1,
      participants: [
        {
          id: 'me',
          name: '나',
          displayName: '나',
          avatarText: '나',
          role: '나',
          status: '회의 참여 중',
          isMe: true,
          muted: false
        }
      ],
      chatMessages: [],
      directContacts: []
    }
    return mockResponse(meeting)
  },
  createEvent: (data) => {
    const event = { id: createId('event'), ...data }
    const calendar =
      boardMockDatabase.calendars[data.teamId] ||
      boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]
    calendar?.events.push(event)
    return mockResponse(event)
  },
  sendMessage: (meetingId, body) => {
    const room =
      boardMockDatabase.meetingRooms[meetingId] || Object.values(boardMockDatabase.meetingRooms)[0]
    const message = {
      id: createId('message'),
      sender: body.sender,
      time: '방금',
      body: body.body,
      mine: true
    }
    room?.chatMessages.push(message)
    return mockResponse(message)
  },
  sendDirectMessage: (meetingId, contactId, body) => {
    const room =
      boardMockDatabase.meetingRooms[meetingId] || Object.values(boardMockDatabase.meetingRooms)[0]
    const contact = room?.directContacts.find((item) => item.id === contactId)
    const message = {
      id: createId('direct'),
      sender: body.sender,
      time: '방금',
      body: body.body,
      mine: true
    }
    contact?.messages.push(message)
    if (contact) {
      contact.preview = message.body
      contact.time = message.time
    }
    return mockResponse(message)
  },
  updateParticipant: (meetingId, participantId, data) => {
    const room =
      boardMockDatabase.meetingRooms[meetingId] || Object.values(boardMockDatabase.meetingRooms)[0]
    const participant = room?.participants.find((item) => item.id === participantId)
    Object.assign(participant, data)
    return mockResponse(participant)
  }
}
