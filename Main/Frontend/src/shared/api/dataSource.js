import { authApi } from '../../features/auth/api'
import { authMockApi } from '../../features/auth/mock/authMockApi'
import { boardApi } from '../../features/board/api'
import { boardMockApi } from '../../features/board/mock/boardMockApi'
import { documentApi } from '../../features/document/api'
import { documentMockApi } from '../../features/document/mock/documentMockApi'
import { notificationApi } from '../../features/notification/api'
import { notificationMockApi } from '../../features/notification/mock/notificationMockApi'
import { userApi } from '../../features/user/api'
import { userMockApi } from '../../features/user/mock/userMockApi'

const globalMockMode = import.meta.env.VITE_USE_MOCK_API !== 'false'

function getMockMode(value) {
  return value === undefined || value === '' ? globalMockMode : value !== 'false'
}

export const useMockApi = globalMockMode
export const mockMode = {
  auth: getMockMode(import.meta.env.VITE_USE_MOCK_AUTH_API),
  board: getMockMode(import.meta.env.VITE_USE_MOCK_BOARD_API),
  document: getMockMode(import.meta.env.VITE_USE_MOCK_DOCUMENT_API),
  meeting: getMockMode(import.meta.env.VITE_USE_MOCK_MEETING_API),
  notification: getMockMode(import.meta.env.VITE_USE_MOCK_NOTIFICATION_API),
  passwordReset: getMockMode(
    import.meta.env.VITE_USE_MOCK_PASSWORD_RESET_API
  ),
  space: getMockMode(import.meta.env.VITE_USE_MOCK_SPACE_API),
  user: getMockMode(import.meta.env.VITE_USE_MOCK_USER_API),
  userSearch: getMockMode(import.meta.env.VITE_USE_MOCK_USER_SEARCH_API),
  userWithdraw: getMockMode(import.meta.env.VITE_USE_MOCK_USER_WITHDRAW_API)
}

const authSource = mockMode.auth ? authMockApi : authApi
const boardSource = mockMode.board ? boardMockApi : boardApi
const meetingSource = mockMode.meeting ? boardMockApi : boardApi
const spaceSource = mockMode.space ? boardMockApi : boardApi
const documentSource = mockMode.document ? documentMockApi : documentApi
const notificationSource = mockMode.notification
  ? notificationMockApi
  : notificationApi
const userSource = mockMode.user ? userMockApi : userApi

export const dataSource = {
  auth: {
    ...authSource,
    resetPassword: mockMode.passwordReset
      ? authMockApi.resetPassword
      : authApi.resetPassword
  },
  board: {
    ...boardSource,
    getDashboard: spaceSource.getDashboard,
    getTeam: spaceSource.getTeam,
    getMembers: spaceSource.getMembers,
    createWorkspace: spaceSource.createWorkspace,
    updateTeam: spaceSource.updateTeam,
    reorderWorkspaces: spaceSource.reorderWorkspaces,
    leaveWorkspace: spaceSource.leaveWorkspace,
    deleteWorkspace: spaceSource.deleteWorkspace,
    transferWorkspaceOwnership: spaceSource.transferWorkspaceOwnership,
    getMeetings: meetingSource.getMeetings,
    joinMeeting: meetingSource.joinMeeting,
    leaveMeeting: meetingSource.leaveMeeting,
    endMeeting: meetingSource.endMeeting,
    getParticipants: meetingSource.getParticipants,
    transferMeetingHost: meetingSource.transferMeetingHost,
    createMeeting: meetingSource.createMeeting
  },
  document: documentSource,
  notification: notificationSource,
  user: {
    ...userSource,
    searchUsers: mockMode.userSearch
      ? userMockApi.searchUsers
      : userApi.searchUsers,
    withdraw: mockMode.userWithdraw
      ? userMockApi.withdraw
      : userApi.withdraw
  }
}
