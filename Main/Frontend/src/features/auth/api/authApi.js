import { request } from '../../../shared/api'

export const authApi = {
  signup: data =>
    request('/api/signup', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  login: data =>
    request('/api/login', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
  logout: () => request('/api/logout', { method: 'POST' })
}
