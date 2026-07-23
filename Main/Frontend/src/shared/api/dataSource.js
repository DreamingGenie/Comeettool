import { authApi } from '../../features/auth/api'
import { authMockApi } from '../../features/auth/mock/authMock'
import { boardApi } from '../../features/board/api'
import { boardMockApi } from '../../features/board/mock/boardMock'
import { userApi } from '../../features/user/api'
import { userMockApi } from '../../features/user/mock/userMock'

export const useMockApi = import.meta.env.VITE_USE_MOCK_API !== 'false'

export const dataSource = {
  auth: useMockApi ? authMockApi : authApi,
  board: useMockApi ? boardMockApi : boardApi,
  user: useMockApi ? userMockApi : userApi
}
