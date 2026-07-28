import { onMounted } from 'vue'
import { userStore } from '../stores/userStore'

export function useUserPage() {
  const load = () => userStore.load()
  onMounted(() => load().catch(() => undefined))

  return {
    userState: userStore.state,
    reloadUser: () => load().catch(() => undefined)
  }
}
