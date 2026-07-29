import { reactive } from 'vue'
import { authSession } from '../../../shared/api'
import {
  normalizeProfile,
  toOnboardingPayload,
  toProfileUpdatePayload
} from '../api/userMapper'
import { dataSource } from '../../../shared/api/dataSource'
import { onboardingOptions, profileOptions } from '../constants/userOptions'

const state = reactive({
  loaded: false,
  loading: false,
  error: '',
  profile: null,
  profileOptions,
  onboarding: onboardingOptions,
  onboardingAnswers: {}
})

export const userStore = {
  state,
  async load(force = false) {
    if (state.loaded && !force) return state
    state.loading = true
    state.error = ''
    try {
      const profile = await dataSource.user.getMe()
      state.profile = normalizeProfile(profile)
      state.loaded = true
      return state
    } catch (error) {
      state.error = error?.message || '사용자 정보를 불러오지 못했습니다.'
      throw error
    } finally {
      state.loading = false
    }
  },
  setProfile(profile) {
    state.profile = normalizeProfile(profile)
  },
  async saveOnboarding(answers) {
    const payload = toOnboardingPayload(answers)
    const profile = await dataSource.user.saveOnboarding(payload)
    state.onboardingAnswers = payload
    state.profile = normalizeProfile(profile)
    state.loaded = true
    return state.profile
  },
  async updateProfile(data) {
    const profile = await dataSource.user.updateProfile(toProfileUpdatePayload(data))
    state.profile = normalizeProfile(profile)
    return state.profile
  },
  async changePassword(data) {
    const result = await dataSource.user.changePassword(data)
    authSession.updateTokens(result)
    return result
  },
  async withdraw() {
    const result = await dataSource.user.withdraw()
    userStore.reset()
    return result
  },
  reset() {
    state.loaded = false
    state.loading = false
    state.error = ''
    state.profile = null
    state.onboardingAnswers = {}
  }
}
