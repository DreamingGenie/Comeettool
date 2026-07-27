import { cloneMockValue, mockResponse } from '../../../shared/api/mockResponse'
import { userMockDatabase } from './userMockDatabase'

export const userMockApi = {
  getMe: () => mockResponse(userMockDatabase.profile),
  getProfileOptions: () => mockResponse(userMockDatabase.profileOptions),
  getOnboardingOptions: () => mockResponse(userMockDatabase.onboarding),
  saveOnboarding: data => {
    userMockDatabase.onboardingAnswers = cloneMockValue(data)
    return mockResponse(userMockDatabase.onboardingAnswers)
  },
  updateProfile: data => {
    Object.assign(userMockDatabase.profile, data)
    return mockResponse(userMockDatabase.profile)
  },
  changePassword: () => mockResponse({ success: true })
}
