import { reactive } from 'vue'
import { authSession } from '../../../shared/api'
import {
  normalizeProfile,
  normalizeUserSummary,
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
  onboardingAnswers: {},
  search: {
    query: '',
    loading: false,
    error: '',
    results: []
  }
})

let latestSearchId = 0

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
  async updateProfileImage(file) {
    const result = await dataSource.user.updateProfileImage(file)
    const profileImage = result?.profileImage || ''
    state.profile = normalizeProfile({
      ...state.profile,
      profileImage
    })
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
  async searchUsers(query) {
    const normalizedQuery = query.trim()
    const searchId = ++latestSearchId

    state.search.query = normalizedQuery
    state.search.error = ''

    if (normalizedQuery.length < 2) {
      state.search.loading = false
      state.search.results = []
      return []
    }

    state.search.loading = true
    try {
      const response = await dataSource.user.searchUsers(normalizedQuery)
      const users = Array.isArray(response)
        ? response
        : response?.users || response?.content || []
      const results = users.map(normalizeUserSummary)

      if (searchId === latestSearchId) {
        state.search.results = results
      }
      return results
    } catch (error) {
      if (searchId === latestSearchId) {
        state.search.error = error?.message || '사용자를 검색하지 못했습니다.'
        state.search.results = []
      }
      throw error
    } finally {
      if (searchId === latestSearchId) {
        state.search.loading = false
      }
    }
  },
  clearSearch() {
    latestSearchId += 1
    state.search.query = ''
    state.search.loading = false
    state.search.error = ''
    state.search.results = []
  },
  reset() {
    latestSearchId += 1
    state.loaded = false
    state.loading = false
    state.error = ''
    state.profile = null
    state.onboardingAnswers = {}
    state.search.query = ''
    state.search.loading = false
    state.search.error = ''
    state.search.results = []
  }
}
