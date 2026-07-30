import { authSession, request } from '../../../shared/api'
import {
  toCreateSpaceRequest,
  toDashboardViewModel,
  toMemberRowsViewModel,
  toTeamViewModel,
  toWorkspaceViewModel
} from '../mappers/spaceMapper'

export const boardApi = {
  getDashboard: async () =>
    toDashboardViewModel(await request('/api/v1/spaces')),
  getTeam: async spaceId =>
    toTeamViewModel(
      await request(`/api/v1/spaces/${spaceId}`),
      authSession.get().userId
    ),
  getMembers: async spaceId =>
    toMemberRowsViewModel(await request(`/api/v1/spaces/${spaceId}`)),
  getActiveMeeting: teamId => request(`/api/teams/${teamId}/meetings/active`),
  getMeetingRoom: meetingId => request(`/api/rooms/${meetingId}`),
  getParticipants: meetingId => request(`/api/rooms/${meetingId}/participants`),
  getInviteMembers: teamId => request(`/api/teams/${teamId}/invite-members`),
  updateTeam: (teamId, data) =>
    request(`/api/teams/${teamId}`, {
      method: 'PUT',
      body: JSON.stringify(data)
    }),
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
  createMeeting: data =>
    request('/api/rooms', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
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
