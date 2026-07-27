import { userMockDatabase } from '../../user/mock/userMockDatabase'
import { mockResponse } from '../../../shared/api/mockResponse'
import { authMockDatabase } from './authMockDatabase'

export const authMockApi = {
  login: credentials => {
    const user = { ...userMockDatabase.profile, email: credentials.email }
    const session = { userId: user.id, accessToken: 'mock-token' }
    authMockDatabase.sessions.push(session)
    return mockResponse({ user, accessToken: session.accessToken })
  },
  signup: form => {
    const user = {
      ...userMockDatabase.profile,
      nickname: form.nickname || userMockDatabase.profile.nickname,
      email: form.email
    }
    authMockDatabase.users.push(user)
    Object.assign(userMockDatabase.profile, user)
    return mockResponse({ user })
  },
  logout: () => {
    authMockDatabase.sessions.splice(0)
    return mockResponse({ success: true })
  }
}
