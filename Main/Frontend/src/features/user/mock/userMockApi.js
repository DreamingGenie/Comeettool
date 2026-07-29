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
  },
  searchUsers: query => {
    const normalizedQuery = query.trim().toLowerCase()
    const matches = userMockDatabase.users.filter(user => {
      const nickname = user.nickname?.toLowerCase() || ''
      const email = user.email?.toLowerCase() || ''
      return nickname.includes(normalizedQuery) || email.includes(normalizedQuery)
    })
    return mockResponse(matches)
  }
}
