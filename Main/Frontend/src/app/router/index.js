import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/:pathMatch(.*)*',
    name: 'commit-tool',
    component: () => import('../../features/board/views/CommitToolView.vue')
  }
]

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})
