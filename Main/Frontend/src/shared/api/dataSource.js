import { authApi } from '../../features/auth/api'
import { authMockApi } from '../../features/auth/mock/authMockApi'
import { boardApi } from '../../features/board/api'
import { boardMockApi } from '../../features/board/mock/boardMockApi'
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
  user: getMockMode(import.meta.env.VITE_USE_MOCK_USER_API)
}

export const dataSource = {
  auth: mockMode.auth ? authMockApi : authApi,
  board: mockMode.board ? boardMockApi : boardApi,
  user: mockMode.user ? userMockApi : userApi
}
