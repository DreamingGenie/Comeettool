import { request } from '../../../shared/api'

export const userApi = {
  getMe: () => request('/api/me'),
  updateProfile: data =>
    request('/api/me', {
      method: 'PUT',
      body: JSON.stringify(data)
    }),
  changePassword: data =>
    request('/api/password', {
      method: 'POST',
      body: JSON.stringify(data)
    })
}
