import { onMounted } from 'vue'
import { authStore } from '../../auth/stores/authStore'
import { userStore } from '../stores/userStore'

export function useUserPage() {
  const load = () => {
    const sessionUserId = authStore.state.userId
    const loadedUserId = userStore.state.profile?.userId
    if (
      userStore.state.loaded &&
      sessionUserId &&
      loadedUserId !== sessionUserId
    ) {
      userStore.reset()
    }
    return userStore.load()
  }
  onMounted(() => load().catch(() => undefined))

  return {
    userState: userStore.state,
    reloadUser: () => userStore.load(true).catch(() => undefined)
  }
}
