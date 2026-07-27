import { authApi } from '../../features/auth/api'
import { authMockApi } from '../../features/auth/mock/authMockApi'
import { boardApi } from '../../features/board/api'
import { boardMockApi } from '../../features/board/mock/boardMockApi'
import { userApi } from '../../features/user/api'
import { userMockApi } from '../../features/user/mock/userMockApi'

export const useMockApi = import.meta.env.VITE_USE_MOCK_API !== 'false'

export const dataSource = {
  auth: useMockApi ? authMockApi : authApi,
  board: useMockApi ? boardMockApi : boardApi,
  user: useMockApi ? userMockApi : userApi
}
