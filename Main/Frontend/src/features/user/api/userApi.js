import { request } from '../../../shared/api'

export const userApi = {
  getMe: () => request('/api/me'),
  getProfileOptions: () => request('/api/profile-options'),
  getOnboardingOptions: () => request('/api/onboarding/options'),
  saveOnboarding: data =>
    request('/api/onboarding', {
      method: 'POST',
      body: JSON.stringify(data)
    }),
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
