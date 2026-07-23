import { createApp } from 'vue'
import App from './app/App.vue'
import router from './app/router'
import './assets/styles/ui-app.css'
import './assets/styles/archive-pages.css'

createApp(App).use(router).mount('#app')
