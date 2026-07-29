import { authMockApi } from '../mock/authMockApi'
import { authApi } from './authApi'

const useMockApi =
  (import.meta.env.VITE_USE_MOCK_AUTH_API ??
    import.meta.env.VITE_USE_MOCK_API) !== 'false'

export const authDataSource = useMockApi ? authMockApi : authApi
