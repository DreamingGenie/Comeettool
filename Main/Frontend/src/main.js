import { createApp } from 'vue'
import App from './app/App.vue'
import router from './app/router'
import { boardStore } from './features/board/stores/boardStore'
import { userStore } from './features/user/stores/userStore'
import './assets/styles/ui-app.css'
import './assets/styles/archive-pages.css'
import './assets/styles/motion.css'

window.addEventListener('auth:expired', () => {
  boardStore.reset()
  userStore.reset()
  if (router.currentRoute.value.name !== 'login') {
    router
      .replace({
        name: 'login',
        query: { redirect: router.currentRoute.value.fullPath }
      })
      .catch(() => undefined)
  }
})

createApp(App).use(router).mount('#app')
