import { userMockDatabase } from '../../user/mock/userMockDatabase'
import { mockResponse } from '../../../shared/api/mockResponse'
import { authMockDatabase } from './authMockDatabase'

export const authMockApi = {
  login: credentials => {
    const user = { ...userMockDatabase.profile, email: credentials.email }
    const session = {
      userId: user.id,
      accessToken: 'mock-access-token',
      refreshToken: 'mock-refresh-token'
    }
    authMockDatabase.sessions.push(session)
    return mockResponse({
      tokenType: 'Bearer',
      accessToken: session.accessToken,
      refreshToken: session.refreshToken,
      userId: session.userId,
      onboarded: Boolean(user.sex && user.age)
    })
  },
  signup: form => {
    const user = {
      ...userMockDatabase.profile,
      nickname: form.nickname || userMockDatabase.profile.nickname,
      email: form.email
    }
    authMockDatabase.users.push(user)
    Object.assign(userMockDatabase.profile, user)
    return mockResponse({
      userId: user.id,
      email: user.email,
      createdAt: new Date().toISOString()
    })
  },
  refreshAccessToken: () =>
    mockResponse({ tokenType: 'Bearer', accessToken: 'mock-access-token-refreshed' }),
  resetPassword: email => mockResponse({ email, requested: true }),
  logout: () => {
    authMockDatabase.sessions.splice(0)
    return mockResponse({ success: true })
  }
}
