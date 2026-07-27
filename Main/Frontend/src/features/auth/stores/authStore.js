import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({
  user: null,
  authenticated: false
})

export const authStore = {
  state,
  async login(credentials) {
    const result = await dataSource.auth.login(credentials)
    state.user = result.user
    state.authenticated = true
    return result
  },
  async signup(form) {
    const result = await dataSource.auth.signup(form)
    state.user = null
    state.authenticated = false
    return result
  },
  async logout() {
    await dataSource.auth.logout()
    state.user = null
    state.authenticated = false
  }
}
