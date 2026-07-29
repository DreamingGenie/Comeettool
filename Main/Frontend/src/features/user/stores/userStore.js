import { reactive } from 'vue'
import { userDataSource } from '../api/userDataSource'

const state = reactive({
  loaded: false,
  loading: false,
  error: '',
  profile: null,
  profileOptions: {
    genders: [],
    ageGroups: [],
    jobGroups: [],
    jobs: [],
    colors: []
  },
  onboarding: {
    steps: [{ title: '', label: '', options: [] }]
  },
  onboardingAnswers: {}
})

export const userStore = {
  state,
  async load() {
    if (state.loaded) return state
    state.loading = true
    state.error = ''
    try {
      const [profile, profileOptions, onboarding] = await Promise.all([
        state.profile || userDataSource.getMe(),
        userDataSource.getProfileOptions(),
        userDataSource.getOnboardingOptions()
      ])
      state.profile = profile
      state.profileOptions = profileOptions
      state.onboarding = onboarding
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
    state.profile = profile
  },
  async saveOnboarding(data) {
    state.onboardingAnswers = { ...data }
    state.profile = await userDataSource.saveOnboarding(data)
    return state.profile
  },
  async updateProfile(data) {
    state.profile = await userDataSource.updateProfile(data)
    return state.profile
  },
  changePassword: data => userDataSource.changePassword(data),
  deleteAccount: () => userDataSource.deleteAccount(),
  searchUsers: query => userDataSource.searchUsers(query),
  reset() {
    state.loaded = false
    state.loading = false
    state.error = ''
    state.profile = null
    state.onboardingAnswers = {}
  }
}
