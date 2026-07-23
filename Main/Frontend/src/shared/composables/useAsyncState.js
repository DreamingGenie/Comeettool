import { ref } from 'vue'

export function useAsyncState() {
  const loading = ref(false)
  const error = ref('')

  async function run(task) {
    loading.value = true
    error.value = ''
    try {
      return await task()
    } catch (reason) {
      error.value = reason?.message || '요청을 처리하지 못했습니다.'
      throw reason
    } finally {
      loading.value = false
    }
  }

  return { loading, error, run }
}
