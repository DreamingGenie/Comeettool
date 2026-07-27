import { boardMockDatabase } from './boardMockDatabase'
import { cloneMockValue, mockResponse } from '../../../shared/api/mockResponse'

const createId = prefix =>
  `${prefix}-${globalThis.crypto?.randomUUID?.() || Date.now().toString(36)}`

export const boardMockApi = {
  getDashboard: () => mockResponse({ workspaces: boardMockDatabase.workspaces }),
  getTeam: teamId =>
    mockResponse(
      boardMockDatabase.teams.find(team => team.id === teamId) ||
        boardMockDatabase.teams[0]
    ),
  getMembers: () => mockResponse(boardMockDatabase.members),
  getActiveMeeting: () =>
    mockResponse(boardMockDatabase.activeMeetings[0] || null),
  getEvents: (teamId, year, month) => {
    const source =
      boardMockDatabase.calendars[teamId] ||
      boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]
    const firstDay = new Date(year, month - 1, 1).getDay()
    const totalDays = new Date(year, month, 0).getDate()
    return mockResponse({
      ...source,
      year,
      month,
      leadingBlankDays: firstDay,
      totalDays,
      events:
        source.year === year && source.month === month
          ? source.events
          : []
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
  getMeetingRoom: meetingId =>
    mockResponse(
      boardMockDatabase.meetingRooms[meetingId] ||
        Object.values(boardMockDatabase.meetingRooms)[0]
    ),
  getParticipants: meetingId =>
    mockResponse(
      (
        boardMockDatabase.meetingRooms[meetingId] ||
        Object.values(boardMockDatabase.meetingRooms)[0]
      )?.participants || []
    ),
  getMessages: meetingId =>
    mockResponse(
      (
        boardMockDatabase.meetingRooms[meetingId] ||
        Object.values(boardMockDatabase.meetingRooms)[0]
      )?.chatMessages || []
    ),
  getInviteMembers: teamId =>
    mockResponse(
      boardMockDatabase.inviteMembers[teamId] ||
        boardMockDatabase.inviteMembers[boardMockDatabase.teams[0]?.id] ||
      []
    ),
  updateTeam: (teamId, data) => {
    const team =
      boardMockDatabase.teams.find(item => item.id === teamId) ||
      boardMockDatabase.teams[0]
    Object.assign(team, data)
    return mockResponse(team)
  },
  inviteMember: (teamId, data) => {
    const targetTeamId =
      boardMockDatabase.inviteMembers[teamId] ? teamId : boardMockDatabase.teams[0]?.id
    const member = {
      id: createId('invite'),
      name: data.email.split('@')[0],
      email: data.email,
      avatarText: data.email.slice(0, 1).toUpperCase(),
      role: data.permission || 'MEMBER'
    }
    boardMockDatabase.inviteMembers[targetTeamId].push(member)
    return mockResponse(member)
  },
  createWorkspace: data => {
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
      memberCount: 1
    })
    boardMockDatabase.calendars[workspace.id] = {
      ...cloneMockValue(
        boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]
      ),
      events: []
    }
    boardMockDatabase.inviteMembers[workspace.id] = []
    return mockResponse(workspace)
  },
  createMeeting: data => {
    const meeting = {
      id: createId('meeting'),
      title: data.title || data.name,
      roomTitle: data.roomTitle || data.title || data.name,
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
  createEvent: data => {
    const event = { id: createId('event'), ...data }
    const calendar =
      boardMockDatabase.calendars[data.teamId] ||
      boardMockDatabase.calendars[boardMockDatabase.teams[0]?.id]
    calendar?.events.push(event)
    return mockResponse(event)
  },
  sendMessage: (meetingId, body) => {
    const room =
      boardMockDatabase.meetingRooms[meetingId] ||
      Object.values(boardMockDatabase.meetingRooms)[0]
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
      boardMockDatabase.meetingRooms[meetingId] ||
      Object.values(boardMockDatabase.meetingRooms)[0]
    const contact = room?.directContacts.find(item => item.id === contactId)
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
      boardMockDatabase.meetingRooms[meetingId] ||
      Object.values(boardMockDatabase.meetingRooms)[0]
    const participant = room?.participants.find(item => item.id === participantId)
    Object.assign(participant, data)
    return mockResponse(participant)
  }
}
