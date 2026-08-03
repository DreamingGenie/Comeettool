import { authSession, request } from '../../../shared/api'
import {
  toCreateSpaceRequest,
  toDashboardViewModel,
  toMemberRowsViewModel,
  toTeamViewModel,
  toUpdatedWorkspaceViewModel,
  toUpdateSpaceRequest,
  toWorkspaceViewModel
} from '../mappers/spaceMapper'
import {
  toMeetingConnectionViewModel,
  toMeetingListViewModel,
  toMeetingParticipantsViewModel,
  toMeetingViewModel
} from '../mappers/meetingMapper'

export const boardApi = {
  getDashboard: async (search = '') => {
    const params = new URLSearchParams()
    const keyword = String(search || '').trim()
    if (keyword) params.set('search', keyword)
    const query = params.toString()

    return toDashboardViewModel(
      await request(`/api/v1/spaces${query ? `?${query}` : ''}`)
    )
  },
  getTeam: async spaceId =>
    toTeamViewModel(
      await request(`/api/v1/spaces/${spaceId}`),
      authSession.get().userId
    ),
  getMembers: async spaceId =>
    toMemberRowsViewModel(await request(`/api/v1/spaces/${spaceId}`)),
  getMeetings: async spaceId =>
    toMeetingListViewModel(
      await request(`/api/v1/spaces/${spaceId}/meetings`)
    ),
  joinMeeting: async meetingId =>
    toMeetingConnectionViewModel(
      await request(`/api/v1/meetings/${meetingId}/join`, {
        method: 'POST'
      })
    ),
  leaveMeeting: meetingId =>
    request(`/api/v1/meetings/${meetingId}/leave`, {
      method: 'POST'
    }),
  endMeeting: meetingId =>
    request(`/api/v1/meetings/${meetingId}/end`, {
      method: 'POST'
    }),
  getParticipants: async meetingId =>
    toMeetingParticipantsViewModel(
      await request(`/api/v1/meetings/${meetingId}/participants`)
    ),
  transferMeetingHost: (meetingId, nextHostParticipantId) =>
    request(`/api/v1/meetings/${meetingId}/grant`, {
      method: 'POST',
      body: JSON.stringify({
        nextHostParticipantId: Number(nextHostParticipantId)
      })
    }),
  getInviteMembers: teamId => request(`/api/teams/${teamId}/invite-members`),
  updateTeam: async (teamId, data) =>
    toUpdatedWorkspaceViewModel(
      await request(`/api/v1/spaces/${teamId}`, {
        method: 'PATCH',
        body: JSON.stringify(toUpdateSpaceRequest(data))
      })
    ),
  reorderWorkspaces: async spaceOrder =>
    toDashboardViewModel(
      await request('/api/v1/spaces/order', {
        method: 'PATCH',
        body: JSON.stringify({
          spaceOrder: spaceOrder.map(spaceId => Number(spaceId))
        })
      })
    ),
  inviteMember: (teamId, data) =>
    request(`/api/teams/${teamId}/invite-members`, {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  getArchive: (teamId, section) =>
    request(`/api/teams/${teamId}/archive/${section}`),
  createWorkspace: async data =>
    toWorkspaceViewModel(
      await request('/api/v1/spaces', {
        method: 'POST',
        body: JSON.stringify(toCreateSpaceRequest(data))
      })
    ),
  leaveWorkspace: spaceId =>
    request(`/api/v1/spaces/${spaceId}/members/me`, {
      method: 'DELETE'
    }),
  deleteWorkspace: spaceId =>
    request(`/api/v1/spaces/${spaceId}`, {
      method: 'DELETE'
    }),
  transferWorkspaceOwnership: (spaceId, newOwnerUserId) =>
    request(`/api/v1/spaces/${spaceId}/owner`, {
      method: 'PATCH',
      body: JSON.stringify({ newOwnerUserId })
    }),
  createMeeting: async data =>
    toMeetingViewModel(
      await request(`/api/v1/spaces/${data.spaceId}/meetings`, {
        method: 'POST',
        body: JSON.stringify({ meetingRoomName: data.meetingRoomName })
      }),
      data
    ),
  getMessages: code => request(`/api/rooms/${code}/messages`),
  sendMessage: (code, body) =>
    request(`/api/rooms/${code}/messages`, {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  sendDirectMessage: (code, userId, body) =>
    request(`/api/rooms/${code}/direct/${userId}/messages`, {
      method: 'POST',
      body: JSON.stringify(body)
    }),
  updateParticipant: (code, userId, data) =>
    request(`/api/rooms/${code}/participants/${userId}`, {
      method: 'PATCH',
      body: JSON.stringify(data)
    }),
  getEvents: (teamId, year, month) =>
    request(`/api/teams/${teamId}/events?year=${year}&month=${month}`),
  createEvent: data =>
    request('/api/events', {
      method: 'POST',
      body: JSON.stringify(data)
    })
}
