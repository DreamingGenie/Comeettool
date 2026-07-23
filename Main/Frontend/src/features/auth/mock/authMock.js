const wait = value => new Promise(resolve => setTimeout(() => resolve(value), 220))

export const authMockApi = {
  login: credentials =>
    wait({ user: { id: 1, nickname: '김인승', email: credentials.email }, accessToken: 'mock-token' }),
  signup: form =>
    wait({ user: { id: 1, nickname: form.nickname || '김인승', email: form.email } }),
  logout: () => wait({ success: true })
}
