import { reactive } from 'vue'
import { authSession } from '../../../shared/api'
import { dataSource } from '../../../shared/api/dataSource'

const storedSession = authSession.get()
const state = reactive({
  user: null,
  authenticated: false,
  initialized: false,
  userId: storedSession.userId,
  onboarded: storedSession.onboarded
})

function resetState() {
  state.user = null
  state.authenticated = false
  state.userId = null
  state.onboarded = false
}

export const authStore = {
  state,
  async login(credentials) {
    const result = await dataSource.auth.login(credentials)
    authSession.save(result)
    state.user = null
    state.authenticated = true
    state.initialized = true
    state.userId = result.userId
    state.onboarded = Boolean(result.onboarded)
    return result
  },
  async signup(form) {
    const result = await dataSource.auth.signup(form)
    resetState()
    return result
  },
  resetPassword(email) {
    return dataSource.auth.resetPassword(email)
  },
  async restore() {
    if (state.initialized) return state.authenticated
    state.initialized = true

    if (!authSession.hasAccessToken()) {
      resetState()
      return false
    }

    try {
      state.user = await dataSource.user.getMe()
      const session = authSession.get()
      state.authenticated = true
      state.userId = session.userId ?? state.user?.userId ?? state.user?.id ?? null
      state.onboarded = session.onboarded
      return true
    } catch {
      authSession.clear()
      resetState()
      return false
    }
  },
  async logout() {
    try {
      await dataSource.auth.logout()
    } finally {
      authSession.clear()
      resetState()
      state.initialized = true
    }
  },
  markOnboarded() {
    state.onboarded = true
    authSession.updateOnboarded(true)
  },
  clearSession() {
    authSession.clear()
    resetState()
    state.initialized = true
  }
}

if (typeof window !== 'undefined') {
  window.addEventListener('auth:expired', resetState)
}
