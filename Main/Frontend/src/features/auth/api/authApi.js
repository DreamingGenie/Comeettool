import { request } from '../../../shared/api'

export const authApi = {
  signup: ({ email, password }) =>
    request('/api/v1/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      auth: false
    }),
  login: ({ email, password }) =>
    request('/api/v1/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
      auth: false
    }),
  refreshAccessToken: refreshToken =>
    request('/api/v1/auth/token/refresh', {
      method: 'POST',
      body: JSON.stringify({ refreshToken }),
      auth: false
    }),
  logout: () => request('/api/v1/auth/logout', { method: 'POST' })
}
