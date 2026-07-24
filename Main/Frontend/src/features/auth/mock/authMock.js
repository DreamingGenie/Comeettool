import { mockUserProfile } from '../../user/mock/userMock'

const wait = value => new Promise(resolve => setTimeout(() => resolve(value), 220))

export const authMockApi = {
  login: credentials =>
    wait({ user: { ...mockUserProfile, email: credentials.email }, accessToken: 'mock-token' }),
  signup: form =>
    wait({ user: { ...mockUserProfile, nickname: form.nickname || mockUserProfile.nickname, email: form.email } }),
  logout: () => wait({ success: true })
}
