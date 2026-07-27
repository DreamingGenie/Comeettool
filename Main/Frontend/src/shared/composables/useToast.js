import { reactive } from 'vue'

const state = reactive({
  message: '',
  visible: false
})

let timer

export function useToast() {
  const notify = (message, duration = 1800) => {
    state.message = message
    state.visible = true
    clearTimeout(timer)
    timer = setTimeout(() => {
      state.visible = false
    }, duration)
  }

  return { toastState: state, notify }
}
