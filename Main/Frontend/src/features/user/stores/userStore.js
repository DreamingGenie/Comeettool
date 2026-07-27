import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({
  loaded: false,
  loading: false,
  error: '',
  profile: null,
  profileOptions: {
    genders: [],
    ageGroups: [],
    jobGroups: [],
    jobs: []
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
        state.profile || dataSource.user.getMe(),
        dataSource.user.getProfileOptions(),
        dataSource.user.getOnboardingOptions()
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
    state.onboardingAnswers = await dataSource.user.saveOnboarding(data)
    return state.onboardingAnswers
  },
  async updateProfile(data) {
    state.profile = await dataSource.user.updateProfile(data)
    return state.profile
  },
  changePassword: data => dataSource.user.changePassword(data)
}
