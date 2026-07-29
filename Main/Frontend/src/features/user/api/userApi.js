import { request } from '../../../shared/api'

export const userApi = {
  getMe: () => request('/api/v1/users/me'),
  saveOnboarding: data =>
    request('/api/v1/users/me', {
      method: 'PATCH',
      body: JSON.stringify(data)
    }),
  updateProfile: data =>
    request('/api/v1/users/me', {
      method: 'PATCH',
      body: JSON.stringify(data)
    }),
  changePassword: ({ currentPassword, newPassword }) =>
    request('/api/v1/users/me/password', {
      method: 'PATCH',
      body: JSON.stringify({ currentPassword, newPassword })
    })
}
