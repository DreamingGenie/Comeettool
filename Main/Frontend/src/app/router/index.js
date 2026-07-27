import { createRouter, createWebHistory } from 'vue-router'

const IntroView = () => import('../../features/auth/views/IntroView.vue')
const LoginView = () => import('../../features/auth/views/LoginView.vue')
const SignupView = () => import('../../features/auth/views/SignupView.vue')
const OnboardingView = () => import('../../features/auth/views/OnboardingView.vue')
const HomeView = () => import('../../features/board/views/HomeView.vue')
const TeamSpaceView = () => import('../../features/board/views/TeamSpaceView.vue')
const MembersView = () => import('../../features/board/views/MembersView.vue')
const DocumentsView = () => import('../../features/board/views/DocumentsView.vue')
const TeamSettingsView = () =>
  import('../../features/board/views/TeamSettingsView.vue')
const MeetingView = () => import('../../features/board/views/MeetingView.vue')
const ProfileView = () => import('../../features/user/views/ProfileView.vue')
const PasswordView = () => import('../../features/user/views/PasswordView.vue')

const archiveRoute = (section, name) => ({
  path: `/teams/:teamId/${section}`,
  name,
  component: DocumentsView,
  props: { section }
})

const routes = [
  { path: '/', name: 'intro', component: IntroView },
  { path: '/login', name: 'login', component: LoginView },
  { path: '/signup', name: 'signup', component: SignupView },
  { path: '/onboarding', name: 'onboarding', component: OnboardingView },
  { path: '/home', name: 'home', component: HomeView },
  {
    path: '/teams/:teamId/schedule',
    name: 'team-schedule',
    component: TeamSpaceView
  },
  {
    path: '/teams/:teamId/members',
    name: 'team-members',
    component: MembersView
  },
  archiveRoute('documents', 'team-documents'),
  archiveRoute('minutes', 'team-minutes'),
  archiveRoute('summary', 'team-summary'),
  archiveRoute('feedback', 'team-feedback'),
  {
    path: '/teams/:teamId/settings',
    name: 'team-settings',
    component: TeamSettingsView
  },
  { path: '/teams', redirect: '/teams/a707/schedule' },
  {
    path: '/teams/:teamId',
    redirect: to => `/teams/${to.params.teamId}/schedule`
  },
  {
    path: '/meetings/:meetingId',
    name: 'meeting',
    component: MeetingView
  },
  { path: '/profile', name: 'profile', component: ProfileView },
  { path: '/password', name: 'password', component: PasswordView },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 })
})
