import { createApp } from 'vue'
import App from './app/App.vue'
import router from './app/router'
import './assets/styles/ui-app.css'
import './assets/styles/archive-pages.css'
import './assets/styles/motion.css'

createApp(App).use(router).mount('#app')
