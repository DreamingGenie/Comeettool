import { request } from '../../../shared/api'
import {
  onboardingOptions,
  profileOptions
} from '../constants/profileOptions'
import {
  mapOnboardingRequest,
  mapProfileRequest,
  mapProfileResponse
} from '../mappers/userMapper'

export const userApi = {
  getMe: () =>
    request('/api/v1/users/me').then(mapProfileResponse),
  getProfileOptions: async () => profileOptions,
  getOnboardingOptions: async () => onboardingOptions,
  saveOnboarding: data =>
    request('/api/v1/users/me', {
      method: 'PATCH',
      body: JSON.stringify(mapOnboardingRequest(data))
    }).then(mapProfileResponse),
  updateProfile: data =>
    request('/api/v1/users/me', {
      method: 'PATCH',
      body: JSON.stringify(mapProfileRequest(data))
    }).then(mapProfileResponse),
  changePassword: data =>
    request('/api/v1/users/me/password', {
      method: 'PATCH',
      body: JSON.stringify(data)
    }),
  deleteAccount: () =>
    request('/api/v1/users/me', { method: 'DELETE' }),
  searchUsers: query =>
    request(`/api/v1/users?query=${encodeURIComponent(query)}`)
}
