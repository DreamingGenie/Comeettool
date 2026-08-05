import { userMockDatabase } from '../../user/mock/userMockDatabase'
import { mockResponse } from '../../../shared/api/mockResponse'
import { authMockDatabase } from './authMockDatabase'

const NICKNAME_MAX_LENGTH = 20

function normalizeEmail(email) {
  return email.trim().toLowerCase()
}

function createDefaultNickname(email) {
  return email.slice(0, email.indexOf('@')).slice(0, NICKNAME_MAX_LENGTH)
}

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
    const email = normalizeEmail(form.email)
    const duplicated = authMockDatabase.users.some(user => user.email === email)
    if (duplicated) {
      const error = new Error('이미 사용 중인 이메일입니다.')
      error.code = 'AUTH_EMAIL_DUPLICATED'
      error.status = 409
      return Promise.reject(error)
    }

    const user = {
      ...userMockDatabase.profile,
      nickname: createDefaultNickname(email),
      email
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
