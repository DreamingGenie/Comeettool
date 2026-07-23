import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  { path: '/', name: 'intro', component: () => import('../../features/board/views/CommitToolView.vue') },
  { path: '/login', name: 'login', component: () => import('../../features/auth/views/LoginView.vue') },
  { path: '/signup', name: 'signup', component: () => import('../../features/auth/views/SignupView.vue') },
  { path: '/onboarding', name: 'onboarding', component: () => import('../../features/auth/views/OnboardingView.vue') },
  { path: '/home', name: 'home', component: () => import('../../features/board/views/HomeView.vue') },
  { path: '/teams/:teamId?', name: 'team-space', component: () => import('../../features/board/views/TeamSpaceView.vue') },
  { path: '/profile', name: 'profile', component: () => import('../../features/user/views/ProfileView.vue') },
  { path: '/password', name: 'password', component: () => import('../../features/user/views/PasswordView.vue') },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
})
