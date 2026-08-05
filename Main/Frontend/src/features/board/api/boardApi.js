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
  toMeetingInviteCandidatesViewModel,
  toMeetingListViewModel,
  toMeetingParticipantsViewModel,
  toMeetingViewModel
} from '../mappers/meetingMapper'
import { toScheduleRequest } from '../mappers/calendarMapper'

export const boardApi = {
  getDashboard: async (search = '') => {
    const params = new URLSearchParams()
    const keyword = String(search || '').trim()
    if (keyword) params.set('search', keyword)
    const query = params.toString()

    return toDashboardViewModel(await request(`/api/v1/spaces${query ? `?${query}` : ''}`))
  },
  getTeam: async (spaceId) =>
    toTeamViewModel(await request(`/api/v1/spaces/${spaceId}`), authSession.get().userId),
  getMembers: async (spaceId) => toMemberRowsViewModel(await request(`/api/v1/spaces/${spaceId}`)),
  getMeetings: async (spaceId) =>
    toMeetingListViewModel(await request(`/api/v1/spaces/${spaceId}/meetings`)),
  joinMeeting: async (meetingId) =>
    toMeetingConnectionViewModel(
      await request(`/api/v1/meetings/${meetingId}/join`, {
        method: 'POST'
      })
    ),
  uploadVadRecording: (meetingId, { sequence, startedAt, endedAt, audio }) => {
    const formData = new FormData()
    formData.append('sequence', String(sequence))
    formData.append('startedAt', String(startedAt))
    formData.append('endedAt', String(endedAt))
    formData.append(
      'audio',
      audio,
      `segment-${String(sequence).padStart(6, '0')}.ogg`
    )
    return request(`/api/v1/meetings/${meetingId}/vad-recordings`, {
      method: 'POST',
      body: formData
    })
  },
  leaveMeeting: (meetingId) =>
    request(`/api/v1/meetings/${meetingId}/leave`, {
      method: 'POST'
    }),
  endMeeting: (meetingId) =>
    request(`/api/v1/meetings/${meetingId}/end`, {
      method: 'POST'
    }),
  getParticipants: async (meetingId) =>
    toMeetingParticipantsViewModel(await request(`/api/v1/meetings/${meetingId}/participants`)),
  transferMeetingHost: (meetingId, nextHostParticipantId) =>
    request(`/api/v1/meetings/${meetingId}/grant`, {
      method: 'POST',
      body: JSON.stringify({
        nextHostParticipantId: Number(nextHostParticipantId)
      })
    }),
  kickMeetingParticipant: (meetingId, participantId) =>
    request(`/api/v1/meetings/${meetingId}/participants/${participantId}`, {
      method: 'DELETE'
    }),
  getMeetingInviteCandidates: async (meetingId, keyword = '') => {
    const params = new URLSearchParams()
    const searchKeyword = String(keyword || '').trim()
    if (searchKeyword) params.set('keyword', searchKeyword)
    const query = params.toString()

    return toMeetingInviteCandidatesViewModel(
      await request(
        `/api/v1/meetings/${meetingId}/invite-candidates${query ? `?${query}` : ''}`
      )
    )
  },
  inviteMeetingMember: (meetingId, userId) =>
    request(`/api/v1/meetings/${meetingId}/invitations/users/${userId}`, {
      method: 'POST'
    }),
  getTeamRoles: (spaceId) => request(`/api/v1/spaces/${spaceId}/members/team-roles`),
  updateTeam: async (teamId, data) =>
    toUpdatedWorkspaceViewModel(
      await request(`/api/v1/spaces/${teamId}`, {
        method: 'PATCH',
        body: JSON.stringify(toUpdateSpaceRequest(data))
      })
    ),
  reorderWorkspaces: async (spaceOrder) =>
    toDashboardViewModel(
      await request('/api/v1/spaces/order', {
        method: 'PATCH',
        body: JSON.stringify({
          spaceOrder: spaceOrder.map((spaceId) => Number(spaceId))
        })
      })
    ),
  inviteMember: (spaceId, targetUserId) =>
    request(`/api/v1/spaces/${spaceId}/invitations`, {
      method: 'POST',
      body: JSON.stringify({ targetUserId: Number(targetUserId) })
    }),
  kickMember: (spaceId, memberId) =>
    request(`/api/v1/spaces/${spaceId}/members/${memberId}`, {
      method: 'DELETE'
    }),
  changeMemberAuthority: (spaceId, memberId, authority) =>
    request(`/api/v1/spaces/${spaceId}/members/${memberId}/authority`, {
      method: 'PATCH',
      body: JSON.stringify({ authority })
    }),
  assignTeamRole: (spaceId, memberId, teamRoleId) =>
    request(`/api/v1/spaces/${spaceId}/members/${memberId}/team-role`, {
      method: 'PATCH',
      body: JSON.stringify({
        teamRoleId: teamRoleId === null ? null : Number(teamRoleId)
      })
    }),
  createTeamRole: (spaceId, data) =>
    request(`/api/v1/spaces/${spaceId}/members/team-roles`, {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  updateTeamRole: (spaceId, teamRoleId, data) =>
    request(`/api/v1/spaces/${spaceId}/members/team-roles/${teamRoleId}`, {
      method: 'PATCH',
      body: JSON.stringify(data)
    }),
  deleteTeamRole: (spaceId, teamRoleId) =>
    request(`/api/v1/spaces/${spaceId}/members/team-roles/${teamRoleId}`, {
      method: 'DELETE'
    }),
  getArchive: (teamId, section) => request(`/api/teams/${teamId}/archive/${section}`),
  createWorkspace: async (data) =>
    toWorkspaceViewModel(
      await request('/api/v1/spaces', {
        method: 'POST',
        body: JSON.stringify(toCreateSpaceRequest(data))
      })
    ),
  leaveWorkspace: (spaceId) =>
    request(`/api/v1/spaces/${spaceId}/members/me`, {
      method: 'DELETE'
    }),
  deleteWorkspace: (spaceId) =>
    request(`/api/v1/spaces/${spaceId}`, {
      method: 'DELETE'
    }),
  transferWorkspaceOwnership: (spaceId, newOwnerUserId) =>
    request(`/api/v1/spaces/${spaceId}/owner`, {
      method: 'PATCH',
      body: JSON.stringify({ newOwnerUserId })
    }),
  createMeeting: async (data) =>
    toMeetingViewModel(
      await request(`/api/v1/spaces/${data.spaceId}/meetings`, {
        method: 'POST',
        body: JSON.stringify({ meetingRoomName: data.meetingRoomName })
      }),
      data
    ),
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
  getMySchedules: () => request('/api/v1/me/schedules'),
  getSchedules: (spaceId) => request(`/api/v1/spaces/${spaceId}/schedules`),
  createSchedule: (spaceId, data) =>
    request(`/api/v1/spaces/${spaceId}/schedules`, {
      method: 'POST',
      body: JSON.stringify(toScheduleRequest(data))
    }),
  updateSchedule: (scheduleId, data) =>
    request(`/api/v1/schedules/${scheduleId}`, {
      method: 'PATCH',
      body: JSON.stringify(toScheduleRequest(data))
    }),
  deleteSchedule: (scheduleId) =>
    request(`/api/v1/schedules/${scheduleId}`, { method: 'DELETE' })
}
