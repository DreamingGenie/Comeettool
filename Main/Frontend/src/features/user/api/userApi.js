import { request } from '../../../shared/api'

export const userApi = {
  getMe: () => request('/api/me'),
  changePassword: data =>
    request('/api/password', {
      method: 'POST',
      body: JSON.stringify(data)
    })
}
