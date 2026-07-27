import { authStore } from '../../auth/stores/authStore'
import { createAuthTemplates } from '../../auth/views/authTemplates'
import { registerAuthEvents } from '../../auth/controllers/registerAuthEvents'
import { userStore } from '../../user/stores/userStore'
import {
  mockUserProfile,
  onboardingMock,
  profileOptions
} from '../../user/mock/userMock'
import { createUserTemplates } from '../../user/views/userTemplates'
import { registerUserEvents } from '../../user/controllers/registerUserEvents'
import {
  createAppShellTemplates,
  renderLogo,
  renderSideIcon
} from '../../../shared/components/appShellTemplates'
import { registerNavigationEvents } from '../../../shared/composables/registerNavigationEvents'
import { boardStore } from '../stores/boardStore'
import { boardUiMock } from '../mock/boardMock'
import { renderCalendar } from '../components/calendarTemplate'
import { createBoardModalTemplates } from '../components/boardModalTemplates'
import { registerBoardEvents } from '../controllers/registerBoardEvents'
import { registerMeetingEvents } from '../controllers/registerMeetingEvents'
import { createDashboardTemplates } from './dashboardTemplates'
import { createTeamTemplates } from './teamTemplates'
import { createMeetingTemplates } from './meetingTemplates'

export function mountCommitTool(app, toast, options = {}) {
  const state = {
    view: options.initialView || 'intro',
    step: 0,
    selected: 0,
    darkMeeting: false
  }
  const controller = new AbortController()
  let dashboardWorkspaces = boardStore.state.workspaces
  let memberRows = boardStore.state.members
  let archives = boardStore.state.archives
  let currentUser = structuredClone(mockUserProfile)

  const {
    team,
    activeMeeting,
    calendar: calendarData,
    meetingRoom,
    inviteMembers,
    teamCreateColors,
    meetingControls
  } = boardUiMock

  const notify = text => {
    toast.textContent = text
    toast.classList.add('show')
    setTimeout(() => toast.classList.remove('show'), 1800)
  }
  const setBusy = busy => app.setAttribute('aria-busy', String(busy))
  const setCurrentUser = profile => {
    currentUser = { ...currentUser, ...profile }
  }
  const runTask = async (task, onSuccess) => {
    setBusy(true)
    try {
      const result = await task()
      onSuccess?.(result)
      return result
    } catch (error) {
      notify(error?.message || '요청을 처리하지 못했습니다.')
      return undefined
    } finally {
      setBusy(false)
    }
  }

  const { topbar, shell, settingsShell } = createAppShellTemplates({
    team,
    getCurrentUser: () => currentUser
  })
  const authViews = createAuthTemplates({ state, onboardingMock, renderLogo })
  const dashboardViews = createDashboardTemplates({
    getWorkspaces: () => dashboardWorkspaces,
    activeMeeting,
    calendarData,
    topbar,
    renderCalendar
  })
  const teamViews = createTeamTemplates({
    team,
    activeMeeting,
    calendarData,
    getMembers: () => memberRows,
    getArchives: () => archives,
    shell,
    renderCalendar
  })
  const userViews = createUserTemplates({
    getCurrentUser: () => currentUser,
    profileOptions,
    settingsShell,
    renderSideIcon
  })
  const meetingTemplates = createMeetingTemplates({
    app,
    state,
    activeMeeting,
    meetingRoom,
    meetingControls
  })
  const renderModal = createBoardModalTemplates({
    getWorkspaces: () => dashboardWorkspaces,
    inviteMembers,
    teamCreateColors
  })
  const views = {
    ...authViews,
    ...dashboardViews,
    ...teamViews,
    ...userViews,
    meeting: meetingTemplates.meeting
  }

  function render(view = state.view) {
    if (view !== state.view && options.onNavigate) {
      options.onNavigate(view)
      return
    }
    state.view = view
    app.innerHTML = views[view]()
    if (view === 'meeting') meetingTemplates.enhanceMeetingHeader()
    window.scrollTo(0, 0)
  }

  registerNavigationEvents({ signal: controller.signal, render })
  registerAuthEvents({
    signal: controller.signal,
    state,
    onboardingMock,
    authStore,
    runTask,
    render,
    setCurrentUser
  })
  registerUserEvents({
    signal: controller.signal,
    userStore,
    runTask,
    render,
    notify,
    setCurrentUser
  })
  registerBoardEvents({
    signal: controller.signal,
    boardStore,
    render,
    renderModal,
    runTask,
    notify
  })
  registerMeetingEvents({
    signal: controller.signal,
    getCurrentUser: () => currentUser,
    notify,
    directList: meetingTemplates.directList,
    directConversation: meetingTemplates.directConversation,
    meetingInfoModal: meetingTemplates.meetingInfoModal,
    meetingMoreMenu: meetingTemplates.meetingMoreMenu
  })

  render()
  setBusy(true)
  Promise.all([boardStore.load(), userStore.load()])
    .then(([boardState, loadedProfile]) => {
      dashboardWorkspaces = boardState.workspaces
      memberRows = boardState.members
      archives = boardState.archives
      setCurrentUser(loadedProfile)
      if (
        ['home', 'members', 'documents', 'minutes', 'summary', 'feedback', 'profile'].includes(
          state.view
        )
      ) {
        render()
      }
    })
    .catch(error => notify(error?.message || '데이터를 불러오지 못했습니다.'))
    .finally(() => setBusy(false))

  return {
    setView(view) {
      if (!views[view]) return
      state.view = view
      render(view)
    },
    destroy() {
      controller.abort()
      document.querySelectorAll('.modal-backdrop').forEach(modal => modal.remove())
    }
  }
}
