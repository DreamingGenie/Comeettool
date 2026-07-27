import { createRouter, createWebHistory } from 'vue-router'

const CommitToolView = () => import('../../features/board/views/CommitToolView.vue')

const viewRoute = (path, name, view) => ({
  path,
  name,
  component: CommitToolView,
  props: { initialView: view },
  meta: { view }
})

const teamSections = [
  ['schedule', 'team-schedule'],
  ['members', 'team-members'],
  ['documents', 'team-documents'],
  ['minutes', 'team-minutes'],
  ['summary', 'team-summary'],
  ['feedback', 'team-feedback'],
  ['settings', 'team-settings']
]

const routes = [
  viewRoute('/', 'intro', 'intro'),
  viewRoute('/login', 'login', 'login'),
  viewRoute('/signup', 'signup', 'signup'),
  viewRoute('/onboarding', 'onboarding', 'onboarding'),
  viewRoute('/home', 'home', 'home'),
  ...teamSections.map(([section, name]) =>
    viewRoute(`/teams/:teamId/${section}`, name, section)
  ),
  {
    path: '/teams',
    redirect: '/teams/a707/schedule'
  },
  {
    path: '/teams/:teamId',
    redirect: to => `/teams/${to.params.teamId}/schedule`
  },
  viewRoute('/meetings/:meetingId', 'meeting', 'meeting'),
  viewRoute('/profile', 'profile', 'profile'),
  viewRoute('/password', 'password', 'password'),
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

export default createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
  scrollBehavior: () => ({ top: 0 })
})
