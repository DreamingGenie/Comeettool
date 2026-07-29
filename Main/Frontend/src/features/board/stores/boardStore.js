import { reactive } from 'vue'
import { boardDataSource } from '../api/boardDataSource'
import { toCalendarViewModel } from '../mappers/calendarMapper'

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

const emptyCalendar = toCalendarViewModel()

const emptyMeetingRoom = {
  totalParticipants: 0,
  participants: [],
  chatMessages: [],
  directContacts: []
}

const state = reactive({
  pendingRequests: 0,
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

const withLoading = async request => {
  state.pendingRequests += 1
  state.loading = true
  state.error = ''

  try {
    return await request()
  } catch (error) {
    state.error = error?.message || '데이터를 불러오지 못했습니다.'
    throw error
  } finally {
    state.pendingRequests = Math.max(0, state.pendingRequests - 1)
    state.loading = state.pendingRequests > 0
  }
}

export const boardStore = {
  state,
  async loadResources(resources = [], context = {}) {
    const {
      teamId = 'a707',
      meetingId = 'be-team-meeting',
      year = state.calendar.year,
      month = state.calendar.month,
      section = 'documents'
    } = context
    const loaders = {
      workspaces: () => boardStore.loadWorkspaces(),
      team: () => boardStore.loadTeam(teamId),
      members: () => boardStore.loadMembers(teamId),
      activeMeeting: () => boardStore.loadActiveMeeting(teamId),
      calendar: () => boardStore.loadCalendar(teamId, year, month),
      meetingRoom: () => boardStore.loadMeetingRoom(meetingId),
      inviteMembers: () => boardStore.loadInviteMembers(teamId),
      archive: () => boardStore.loadArchive(teamId, section)
    }
    const uniqueResources = [...new Set(resources)]
    const unknownResource = uniqueResources.find(resource => !loaders[resource])

    if (unknownResource) {
      throw new Error(`지원하지 않는 board resource입니다: ${unknownResource}`)
    }

    await Promise.all(uniqueResources.map(resource => loaders[resource]()))
    return state
  },
  async loadWorkspaces() {
    const dashboard = await withLoading(() => boardDataSource.getDashboard())
    state.workspaces = dashboard?.workspaces || []
    return state.workspaces
  },
  async loadTeam(teamId) {
    const team = await withLoading(() => boardDataSource.getTeam(teamId))
    state.team = team || { ...emptyTeam }
    state.currentTeamId = teamId
    return state.team
  },
  async loadMembers(teamId) {
    const members = await withLoading(() => boardDataSource.getMembers(teamId))
    state.members = members || []
    return state.members
  },
  async loadActiveMeeting(teamId) {
    const meeting = await withLoading(() =>
      boardDataSource.getActiveMeeting(teamId)
    )
    state.activeMeeting = meeting || { ...emptyMeeting }
    return state.activeMeeting
  },
  async loadCalendar(teamId, year, month) {
    const response = await withLoading(() =>
      boardDataSource.getEvents(teamId, year, month)
    )
    state.calendar = toCalendarViewModel(response, { year, month })
    return state.calendar
  },
  async loadMeetingRoom(meetingId) {
    const [meetingRoom, participants, messages] = await withLoading(() =>
      Promise.all([
        boardDataSource.getMeetingRoom(meetingId),
        boardDataSource.getParticipants(meetingId),
        boardDataSource.getMessages(meetingId)
      ])
    )
    state.meetingRoom = {
      ...(meetingRoom || emptyMeetingRoom),
      participants: participants || meetingRoom?.participants || [],
      chatMessages: messages || meetingRoom?.chatMessages || []
    }
    state.currentMeetingId = meetingId
    return state.meetingRoom
  },
  async loadInviteMembers(teamId) {
    const members = await withLoading(() =>
      boardDataSource.getInviteMembers(teamId)
    )
    state.inviteMembers = members || []
    return state.inviteMembers
  },
  async loadArchive(teamId, section) {
    const result = await withLoading(() =>
      boardDataSource.getArchive(teamId, section)
    )
    state.archives[section] = result?.rows || result || []
    state.archiveStats[section] = result?.stats || null
    return state.archives[section]
  },
  async createWorkspace(data) {
    const workspace = await boardDataSource.createWorkspace(data)
    state.workspaces.push(workspace)
    return workspace
  },
  async createMeeting(data) {
    const meeting = await boardDataSource.createMeeting(data)
    state.activeMeeting = { ...state.activeMeeting, ...meeting }
    return meeting
  },
  async createEvent(data) {
    const event = await boardDataSource.createEvent(data)
    state.calendar.events.push(event)
    return event
  },
  async updateTeam(teamId, data) {
    state.team = await boardDataSource.updateTeam(teamId, data)
    const workspace = state.workspaces.find(item => item.id === teamId)
    if (workspace) {
      workspace.name = state.team.name
      workspace.badge = state.team.badge
    }
    return state.team
  },
  async inviteMember(teamId, data) {
    const member = await boardDataSource.inviteMember(teamId, data)
    state.inviteMembers.push(member)
    return member
  },
  async sendMessage(meetingId, data) {
    const message = await boardDataSource.sendMessage(meetingId, data)
    state.meetingRoom.chatMessages.push(message)
    return message
  },
  async sendDirectMessage(meetingId, contactId, data) {
    const message = await boardDataSource.sendDirectMessage(
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
    const participant = await boardDataSource.updateParticipant(
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
