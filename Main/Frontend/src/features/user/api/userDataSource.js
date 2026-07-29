import { userMockApi } from '../mock/userMockApi'
import { userApi } from './userApi'

const useMockApi =
  (import.meta.env.VITE_USE_MOCK_USER_API ??
    import.meta.env.VITE_USE_MOCK_API) !== 'false'

export const userDataSource = useMockApi ? userMockApi : userApi
