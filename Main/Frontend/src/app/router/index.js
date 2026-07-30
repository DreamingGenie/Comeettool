import { createRouter, createWebHistory } from 'vue-router'
import { authStore } from '../../features/auth/stores/authStore'

const IntroView = () => import('../../features/auth/views/IntroView.vue')
const LoginView = () => import('../../features/auth/views/LoginView.vue')
const SignupView = () => import('../../features/auth/views/SignupView.vue')
const OnboardingView = () => import('../../features/auth/views/OnboardingView.vue')
const PasswordResetView = () =>
  import('../../features/auth/views/PasswordResetView.vue')
const HomeView = () => import('../../features/board/views/HomeView.vue')
const TeamSpaceView = () => import('../../features/board/views/TeamSpaceView.vue')
const MembersView = () => import('../../features/board/views/MembersView.vue')
const DocumentsView = () => import('../../features/board/views/DocumentsView.vue')
const DocumentListView = () => import('../../features/document/views/DocumentListView.vue')
const DocumentEditorView = () => import('../../features/document/views/DocumentEditorView.vue')
const TeamSettingsView = () =>
  import('../../features/board/views/TeamSettingsView.vue')
const MeetingView = () => import('../../features/board/views/MeetingView.vue')
const ProfileView = () => import('../../features/user/views/ProfileView.vue')
const PasswordView = () => import('../../features/user/views/PasswordView.vue')
const AccountView = () => import('../../features/user/views/AccountView.vue')

const archiveRoute = (section, name) => ({
  path: `/teams/:teamId/${section}`,
  name,
  component: DocumentsView,
  props: { section },
  meta: { requiresAuth: true }
})

const routes = [
  { path: '/', name: 'intro', component: IntroView },
  {
    path: '/login',
    name: 'login',
    component: LoginView,
    meta: { guestOnly: true }
  },
  {
    path: '/signup',
    name: 'signup',
    component: SignupView,
    meta: { guestOnly: true }
  },
  {
    path: '/password/reset',
    name: 'password-reset',
    component: PasswordResetView,
    meta: { guestOnly: true }
  },
  {
    path: '/onboarding',
    name: 'onboarding',
    component: OnboardingView,
    meta: { requiresAuth: true }
  },
  {
    path: '/home',
    name: 'home',
    component: HomeView,
    meta: { requiresAuth: true }
  },
  {
    path: '/teams/:teamId/schedule',
    name: 'team-schedule',
    component: TeamSpaceView,
    meta: { requiresAuth: true }
  },
  {
    path: '/teams/:teamId/members',
    name: 'team-members',
    component: MembersView,
    meta: { requiresAuth: true }
  },
  {
    path: '/teams/:teamId/documents',
    name: 'team-documents',
    component: DocumentListView,
    meta: { requiresAuth: true }
  },
  {
    path: '/teams/:teamId/documents/:documentId',
    name: 'team-document-editor',
    component: DocumentEditorView,
    meta: { requiresAuth: true }
  },
  archiveRoute('minutes', 'team-minutes'),
  archiveRoute('summary', 'team-summary'),
  archiveRoute('feedback', 'team-feedback'),
  {
    path: '/teams/:teamId/settings',
    name: 'team-settings',
    component: TeamSettingsView,
    meta: { requiresAuth: true }
  },
  {
    path: '/teams',
    redirect: '/home',
    meta: { requiresAuth: true }
  },
  {
    path: '/teams/:teamId',
    redirect: to => `/teams/${to.params.teamId}/schedule`,
    meta: { requiresAuth: true }
  },
  {
    path: '/meetings/:meetingId',
    name: 'meeting',
    component: MeetingView,
    meta: { requiresAuth: true }
  },
  {
    path: '/profile',
    name: 'profile',
    component: ProfileView,
    meta: { requiresAuth: true }
  },
  {
    path: '/password',
    name: 'password',
    component: PasswordView,
    meta: { requiresAuth: true }
  },
  {
    path: '/account',
    name: 'account',
    component: AccountView,
    meta: { requiresAuth: true }
  },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.beforeEach(async to => {
  await authStore.restore()

  if (to.meta.requiresAuth && !authStore.state.authenticated) {
    return {
      name: 'login',
      query: { redirect: to.fullPath }
    }
  }

  if (to.meta.guestOnly && authStore.state.authenticated) {
    return { name: authStore.state.onboarded ? 'home' : 'onboarding' }
  }

  if (
    to.name === 'onboarding' &&
    authStore.state.authenticated &&
    authStore.state.onboarded
  ) {
    return { name: 'home' }
  }

  return true
})

export default router
