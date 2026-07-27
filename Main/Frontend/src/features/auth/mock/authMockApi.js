import { userMockDatabase } from '../../user/mock/userMockDatabase'
import { authMockDatabase } from './authMockDatabase'

const wait = value =>
  new Promise(resolve => setTimeout(() => resolve(structuredClone(value)), 220))

export const authMockApi = {
  login: credentials => {
    const user = { ...userMockDatabase.profile, email: credentials.email }
    const session = { userId: user.id, accessToken: 'mock-token' }
    authMockDatabase.sessions.push(session)
    return wait({ user, accessToken: session.accessToken })
  },
  signup: form => {
    const user = {
      ...userMockDatabase.profile,
      nickname: form.nickname || userMockDatabase.profile.nickname,
      email: form.email
    }
    authMockDatabase.users.push(user)
    Object.assign(userMockDatabase.profile, user)
    return wait({ user })
  },
  logout: () => {
    authMockDatabase.sessions.splice(0)
    return wait({ success: true })
  }
}
