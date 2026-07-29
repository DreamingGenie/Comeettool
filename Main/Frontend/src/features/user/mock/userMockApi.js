import { cloneMockValue, mockResponse } from '../../../shared/api/mockResponse'
import { userMockDatabase } from './userMockDatabase'

export const userMockApi = {
  getMe: () => mockResponse(userMockDatabase.profile),
  saveOnboarding: data => {
    userMockDatabase.onboardingAnswers = cloneMockValue(data)
    Object.assign(userMockDatabase.profile, data)
    return mockResponse(userMockDatabase.profile)
  },
  updateProfile: data => {
    Object.assign(userMockDatabase.profile, data)
    return mockResponse(userMockDatabase.profile)
  },
  changePassword: () =>
    mockResponse({
      tokenType: 'Bearer',
      accessToken: 'mock-access-token-after-password-change',
      refreshToken: 'mock-refresh-token-after-password-change'
    }),
  withdraw: () => {
    userMockDatabase.withdrawn = true
    return mockResponse({ success: true })
  }
}
