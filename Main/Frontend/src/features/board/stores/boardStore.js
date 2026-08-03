import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'
import { toCalendarViewModel } from '../mappers/calendarMapper'

const emptyTeam = {
  id: '',
  badge: '',
  eyebrow: '',
  name: '',
  role: '',
  memberCount: 0,
  description: '',
  ownerId: null,
  members: [],
  colorOptions: [],
  defaultMemberRoles: []
}

const emptyMeeting = {
  id: '',
  teamId: '',
  title: '',
  roomTitle: '',
  status: '',
  description: '',
  date: '',
  time: '',
  agendaTime: '',
  participantCount: 0,
  avatars: [],
  hostId: null,
  isInMeeting: false
}

const emptyCalendar = toCalendarViewModel()

const emptyMeetingRoom = {
  connection: null,
  connectionStatus: 'disconnected',
  totalParticipants: 0,
  participants: [],
  chatMessages: [],
  directContacts: []
}

let workspaceLoadSequence = 0

const state = reactive({
  pendingRequests: 0,
  loading: false,
  error: '',
  currentTeamId: '',
  currentMeetingId: '',
  workspaceQuery: '',
  workspaces: [],
  team: { ...emptyTeam },
  members: [],
  meetings: [],
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

function resetState() {
  workspaceLoadSequence += 1
  state.pendingRequests = 0
  state.loading = false
  state.error = ''
  state.currentTeamId = ''
  state.currentMeetingId = ''
  state.workspaceQuery = ''
  state.workspaces = []
  state.team = { ...emptyTeam }
  state.members = []
  state.meetings = []
  state.activeMeeting = { ...emptyMeeting }
  state.calendar = toCalendarViewModel()
  state.archives = {
    documents: [],
    minutes: [],
    summary: [],
    feedback: []
  }
  state.archiveStats = {
    documents: null,
    minutes: null,
    summary: null,
    feedback: null
  }
  state.meetingRoom = {
    ...emptyMeetingRoom,
    connection: null,
    participants: [],
    chatMessages: [],
    directContacts: []
  }
  state.inviteMembers = []
}

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

function removeWorkspaceFromState(spaceId) {
  state.workspaces = state.workspaces.filter(
    workspace => String(workspace.id) !== String(spaceId)
  )
  if (String(state.currentTeamId) === String(spaceId)) {
    state.currentTeamId = ''
    state.team = { ...emptyTeam, members: [] }
    state.members = []
  }
}

export const boardStore = {
  state,
  async loadResources(resources = [], context = {}) {
    const {
      teamId = '',
      meetingId = '',
      year = state.calendar.year,
      month = state.calendar.month,
      section = 'documents'
    } = context
    const loaders = {
      workspaces: () => boardStore.loadWorkspaces(),
      team: () => boardStore.loadTeam(teamId),
      members: () => boardStore.loadMembers(teamId),
      activeMeeting: () => boardStore.loadMeetings(teamId),
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

    if (uniqueResources.includes('workspaces')) {
      await loaders.workspaces()
    }
    await Promise.all(
      uniqueResources
        .filter(resource => resource !== 'workspaces')
        .map(resource => loaders[resource]())
    )
    return state
  },
  async loadWorkspaces(search = '') {
    const requestSequence = ++workspaceLoadSequence
    const keyword = String(search || '').trim()
    const dashboard = await withLoading(() =>
      dataSource.board.getDashboard(keyword)
    )
    if (requestSequence !== workspaceLoadSequence) return state.workspaces

    state.workspaceQuery = keyword
    state.workspaces = dashboard?.workspaces || []
    return state.workspaces
  },
  async reorderWorkspaces(spaceOrder) {
    const normalizedOrder = spaceOrder.map(String)
    const previousWorkspaces = [...state.workspaces]
    const workspaceMap = new Map(
      previousWorkspaces.map(workspace => [String(workspace.id), workspace])
    )
    const reordered = normalizedOrder
      .map(spaceId => workspaceMap.get(spaceId))
      .filter(Boolean)
    const remaining = previousWorkspaces.filter(
      workspace => !normalizedOrder.includes(String(workspace.id))
    )

    state.workspaces = [...reordered, ...remaining]
    try {
      const dashboard = await dataSource.board.reorderWorkspaces(normalizedOrder)
      state.workspaces = dashboard?.workspaces || state.workspaces
      return state.workspaces
    } catch (error) {
      state.workspaces = previousWorkspaces
      throw error
    }
  },
  async loadTeam(teamId) {
    const team = await withLoading(() => dataSource.board.getTeam(teamId))
    state.team = team || { ...emptyTeam }
    state.currentTeamId = teamId
    return state.team
  },
  async loadMembers(teamId) {
    const members = await withLoading(() => dataSource.board.getMembers(teamId))
    state.members = members || []
    return state.members
  },
  async loadMeetings(teamId, options = {}) {
    const resolvedTeamId =
      String(teamId || state.currentTeamId || state.workspaces[0]?.id || '')
    if (!resolvedTeamId) {
      state.meetings = []
      state.activeMeeting = { ...emptyMeeting }
      return state.meetings
    }

    const requestMeetings = () => dataSource.board.getMeetings(resolvedTeamId)
    const meetings = options.silent
      ? await requestMeetings()
      : await withLoading(requestMeetings)
    state.meetings = meetings || []
    state.activeMeeting = state.meetings[0] || { ...emptyMeeting }
    return state.meetings
  },
  async loadActiveMeeting(teamId) {
    await boardStore.loadMeetings(teamId)
    return state.activeMeeting
  },
  async loadCalendar(teamId, year, month) {
    const response = await withLoading(() =>
      dataSource.board.getEvents(teamId, year, month)
    )
    state.calendar = toCalendarViewModel(response, { year, month })
    return state.calendar
  },
  async loadMeetingRoom(meetingId) {
    const connection = await withLoading(() =>
      dataSource.board.joinMeeting(meetingId)
    )
    state.meetingRoom = {
      ...emptyMeetingRoom,
      connection,
      connectionStatus: 'connecting',
      participants: [],
      chatMessages: [],
      directContacts: []
    }
    state.currentMeetingId = meetingId
    const selectedMeeting = state.meetings.find(
      meeting => String(meeting.id) === String(meetingId)
    )
    if (selectedMeeting) state.activeMeeting = selectedMeeting
    await boardStore.loadMeetingParticipants(meetingId)
    return state.meetingRoom
  },
  async loadMeetingParticipants(meetingId = state.currentMeetingId) {
    if (!meetingId) return []
    const participants = await withLoading(() =>
      dataSource.board.getParticipants(meetingId)
    )
    state.meetingRoom.participants = participants || []
    state.meetingRoom.totalParticipants = state.meetingRoom.participants.length
    return state.meetingRoom.participants
  },
  async loadInviteMembers(teamId) {
    const members = await withLoading(() =>
      dataSource.board.getInviteMembers(teamId)
    )
    state.inviteMembers = members || []
    return state.inviteMembers
  },
  async loadArchive(teamId, section) {
    const result = await withLoading(() =>
      dataSource.board.getArchive(teamId, section)
    )
    state.archives[section] = result?.rows || result || []
    state.archiveStats[section] = result?.stats || null
    return state.archives[section]
  },
  async createWorkspace(data) {
    const workspace = await dataSource.board.createWorkspace(data)
    state.workspaces.unshift(workspace)
    return workspace
  },
  async leaveWorkspace(spaceId) {
    await withLoading(() => dataSource.board.leaveWorkspace(spaceId))
    removeWorkspaceFromState(spaceId)
  },
  async deleteWorkspace(spaceId) {
    await withLoading(() => dataSource.board.deleteWorkspace(spaceId))
    removeWorkspaceFromState(spaceId)
  },
  async transferWorkspaceOwnership(spaceId, newOwnerUserId) {
    const result = await withLoading(() =>
      dataSource.board.transferWorkspaceOwnership(
        spaceId,
        Number(newOwnerUserId)
      )
    )
    await Promise.all([
      boardStore.loadTeam(spaceId),
      boardStore.loadWorkspaces()
    ])
    return result
  },
  async createMeeting(data) {
    const meeting = await withLoading(() =>
      dataSource.board.createMeeting({
        spaceId: data.teamId,
        teamId: data.teamId,
        meetingRoomName: data.name
      })
    )
    state.meetings = [meeting, ...state.meetings]
    state.activeMeeting = meeting
    return meeting
  },
  async leaveMeeting(meetingId = state.currentMeetingId) {
    const result = await withLoading(() =>
      dataSource.board.leaveMeeting(meetingId)
    )
    state.meetingRoom.connectionStatus = 'leaving'
    return result
  },
  async endMeeting(meetingId = state.currentMeetingId) {
    await withLoading(() => dataSource.board.endMeeting(meetingId))
    state.meetings = state.meetings.filter(
      meeting => String(meeting.id) !== String(meetingId)
    )
    state.activeMeeting = state.meetings[0] || { ...emptyMeeting }
    state.meetingRoom.connectionStatus = 'ending'
  },
  async transferMeetingHost(meetingId, nextHostParticipantId) {
    const result = await withLoading(() =>
      dataSource.board.transferMeetingHost(
        meetingId,
        nextHostParticipantId
      )
    )
    if (state.meetingRoom.connection) {
      state.meetingRoom.connection.isHost = false
    }
    await boardStore.loadMeetingParticipants(meetingId)
    return result
  },
  setMeetingConnectionStatus(status) {
    state.meetingRoom.connectionStatus = status
  },
  clearMeetingRoom() {
    state.currentMeetingId = ''
    state.meetingRoom = {
      ...emptyMeetingRoom,
      connection: null,
      participants: [],
      chatMessages: [],
      directContacts: []
    }
  },
  async createEvent(data) {
    const event = await dataSource.board.createEvent(data)
    state.calendar.events.push(event)
    return event
  },
  async updateTeam(teamId, data) {
    const updatedTeam = await withLoading(() =>
      dataSource.board.updateTeam(teamId, data)
    )
    state.team = {
      ...state.team,
      ...updatedTeam,
      members: state.team.members
    }
    const workspaceIndex = state.workspaces.findIndex(
      item => String(item.id) === String(teamId)
    )
    if (workspaceIndex >= 0) {
      state.workspaces[workspaceIndex] = {
        ...state.workspaces[workspaceIndex],
        ...updatedTeam
      }
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
  },
  reset() {
    resetState()
  }
}
