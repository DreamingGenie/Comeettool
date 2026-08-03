import { request } from '../../../shared/api'

export const notificationApi = {
  getInvitations: () => request('/api/v1/invitations/me'),
  acceptInvitation: (invitationId) =>
    request(`/api/v1/invitations/${invitationId}/accept`, {
      method: 'POST'
    }),
  rejectInvitation: (invitationId) =>
    request(`/api/v1/invitations/${invitationId}/reject`, {
      method: 'POST'
    })
}
