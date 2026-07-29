import { boardMockApi } from '../mock/boardMockApi'
import { boardApi } from './boardApi'

const useMockApi =
  (import.meta.env.VITE_USE_MOCK_BOARD_API ??
    import.meta.env.VITE_USE_MOCK_API) !== 'false'

export const boardDataSource = useMockApi ? boardMockApi : boardApi
