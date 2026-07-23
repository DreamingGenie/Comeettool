const profile = {
  id: 1,
  nickname: '김인승',
  phone: '010-1234-5678',
  ageGroup: '20 - 30',
  jobGroup: '소프트웨어 개발',
  job: 'Frontend Developer'
}

const wait = value => new Promise(resolve => setTimeout(() => resolve(structuredClone(value)), 180))

export const userMockApi = {
  getMe: () => wait(profile),
  updateProfile: data => wait(Object.assign(profile, data)),
  changePassword: () => wait({ success: true })
}
