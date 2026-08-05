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
  updateProfileImage: file => {
    const formData = new FormData()
    formData.append('profileImage', file)
    return request('/api/v1/users/me/profile-image', {
      method: 'PATCH',
      body: formData
    })
  },
  changePassword: ({ currentPassword, newPassword }) =>
    request('/api/v1/users/me/password', {
      method: 'PATCH',
      body: JSON.stringify({ currentPassword, newPassword })
    }),
  withdraw: () =>
    request('/api/v1/users/me', {
      method: 'DELETE'
    }),
  searchUsers: query =>
    request(`/api/v1/users?query=${encodeURIComponent(query)}`)
}
