import { reactive } from 'vue'

const state = reactive({
  message: '',
  visible: false,
  placement: 'bottom-right'
})

let timer

export function useToast() {
  const notify = (message, duration = 1800, options = {}) => {
    state.message = message
    state.placement = options.placement || 'bottom-right'
    state.visible = true
    clearTimeout(timer)
    timer = setTimeout(() => {
      state.visible = false
    }, duration)
  }

  return { toastState: state, notify }
}
