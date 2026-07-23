import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'

const state = reactive({ profile: null })

export const userStore = {
  state,
  async load() {
    state.profile = await dataSource.user.getMe()
    return state.profile
  },
  async updateProfile(data) {
    state.profile = await dataSource.user.updateProfile(data)
    return state.profile
  },
  changePassword: data => dataSource.user.changePassword(data)
}
