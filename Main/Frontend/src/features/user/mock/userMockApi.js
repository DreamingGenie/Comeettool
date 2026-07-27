import { userMockDatabase } from './userMockDatabase'

const wait = value =>
  new Promise(resolve => setTimeout(() => resolve(structuredClone(value)), 180))

export const userMockApi = {
  getMe: () => wait(userMockDatabase.profile),
  getProfileOptions: () => wait(userMockDatabase.profileOptions),
  getOnboardingOptions: () => wait(userMockDatabase.onboarding),
  saveOnboarding: data => {
    userMockDatabase.onboardingAnswers = structuredClone(data)
    return wait(userMockDatabase.onboardingAnswers)
  },
  updateProfile: data => {
    Object.assign(userMockDatabase.profile, data)
    return wait(userMockDatabase.profile)
  },
  changePassword: () => wait({ success: true })
}
