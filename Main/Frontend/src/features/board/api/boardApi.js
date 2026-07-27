import { request } from '../../../shared/api'

export const boardApi = {
  getDashboard: () => request('/api/dashboard'),
  getTeam: teamId => request(`/api/teams/${teamId}`),
  getMembers: teamId => request(`/api/teams/${teamId}/members`),
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
  createWorkspace: data =>
    request('/api/workspaces', {
      method: 'POST',
      body: JSON.stringify(data)
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
