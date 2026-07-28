import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const emptyTeam = {
  id: '',
  badge: '',
  eyebrow: '',
  name: '',
  role: '',
  memberCount: 0,
  description: '',
  colorOptions: [],
  defaultMemberRoles: []
}

const emptyMeeting = {
  id: '',
  title: '',
  roomTitle: '',
  status: '',
  description: '',
  date: '',
  time: '',
  agendaTime: '',
  participantCount: 0,
  avatars: []
}

const emptyCalendar = {
  year: new Date().getFullYear(),
  month: new Date().getMonth() + 1,
  leadingBlankDays: 0,
  totalDays: 31,
  weekdays: ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'],
  events: []
}

const emptyMeetingRoom = {
  totalParticipants: 0,
  participants: [],
  chatMessages: [],
  directContacts: []
}

const state = reactive({
  loaded: false,
  loading: false,
  error: '',
  currentTeamId: '',
  currentMeetingId: '',
  workspaces: [],
  team: { ...emptyTeam },
  members: [],
  activeMeeting: { ...emptyMeeting },
  calendar: { ...emptyCalendar },
  archives: {
    documents: [],
    minutes: [],
    summary: [],
    feedback: []
  },
  archiveStats: {
    documents: null,
    minutes: null,
    summary: null,
    feedback: null
  },
  meetingRoom: { ...emptyMeetingRoom },
  inviteMembers: []
})

const archiveSections = ['documents', 'minutes', 'summary', 'feedback']

export const boardStore = {
  state,
  async load({
    teamId = 'a707',
    meetingId = 'be-team-meeting',
    year = state.calendar.year,
    month = state.calendar.month
  } = {}) {
    if (
      state.loaded &&
      state.currentTeamId === teamId &&
      state.currentMeetingId === meetingId
    ) {
      return state
    }
    state.loading = true
    state.error = ''
    try {
      const [
      dashboard,
      team,
      loadedMembers,
      activeMeeting,
      calendar,
      meetingRoom,
      participants,
      messages,
      inviteMembers,
      ...loadedArchives
      ] = await Promise.all([
        dataSource.board.getDashboard(),
        dataSource.board.getTeam(teamId),
        dataSource.board.getMembers(teamId),
        dataSource.board.getActiveMeeting(teamId),
        dataSource.board.getEvents(teamId, year, month),
        dataSource.board.getMeetingRoom(meetingId),
        dataSource.board.getParticipants(meetingId),
        dataSource.board.getMessages(meetingId),
        dataSource.board.getInviteMembers(teamId),
        ...archiveSections.map(async section => [
          section,
          await dataSource.board.getArchive(teamId, section)
        ])
      ])

      state.workspaces = dashboard?.workspaces || []
      state.team = team || { ...emptyTeam }
      state.members = loadedMembers || []
      state.activeMeeting = activeMeeting || { ...emptyMeeting }
      state.calendar = calendar || { ...emptyCalendar }
      state.meetingRoom = {
        ...(meetingRoom || emptyMeetingRoom),
        participants: participants || meetingRoom?.participants || [],
        chatMessages: messages || meetingRoom?.chatMessages || []
      }
      state.inviteMembers = inviteMembers || []

      loadedArchives.forEach(([section, result]) => {
        state.archives[section] = result?.rows || result || []
        state.archiveStats[section] = result?.stats || null
      })

      state.currentTeamId = teamId
      state.currentMeetingId = meetingId
      state.loaded = true
      return state
    } catch (error) {
      state.error = error?.message || '데이터를 불러오지 못했습니다.'
      throw error
    } finally {
      state.loading = false
    }
  },
  async loadCalendar(teamId, year, month) {
    state.calendar = await dataSource.board.getEvents(teamId, year, month)
    return state.calendar
  },
  async createWorkspace(data) {
    const workspace = await dataSource.board.createWorkspace(data)
    state.workspaces.push(workspace)
    return workspace
  },
  async createMeeting(data) {
    const meeting = await dataSource.board.createMeeting(data)
    state.activeMeeting = { ...state.activeMeeting, ...meeting }
    return meeting
  },
  async createEvent(data) {
    const event = await dataSource.board.createEvent(data)
    state.calendar.events.push(event)
    return event
  },
  async updateTeam(teamId, data) {
    state.team = await dataSource.board.updateTeam(teamId, data)
    const workspace = state.workspaces.find(item => item.id === teamId)
    if (workspace) {
      workspace.name = state.team.name
      workspace.badge = state.team.badge
    }
    return state.team
  },
  async inviteMember(teamId, data) {
    const member = await dataSource.board.inviteMember(teamId, data)
    state.inviteMembers.push(member)
    return member
  },
  async sendMessage(meetingId, data) {
    const message = await dataSource.board.sendMessage(meetingId, data)
    state.meetingRoom.chatMessages.push(message)
    return message
  },
  async sendDirectMessage(meetingId, contactId, data) {
    const message = await dataSource.board.sendDirectMessage(
      meetingId,
      contactId,
      data
    )
    const contact = state.meetingRoom.directContacts.find(item => item.id === contactId)
    contact?.messages.push(message)
    if (contact) {
      contact.preview = message.body
      contact.time = message.time
    }
    return message
  },
  async updateParticipant(meetingId, participantId, data) {
    const participant = await dataSource.board.updateParticipant(
      meetingId,
      participantId,
      data
    )
    const index = state.meetingRoom.participants.findIndex(
      item => item.id === participantId
    )
    if (index >= 0) state.meetingRoom.participants[index] = participant
    return participant
  }
}
