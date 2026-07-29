import { cloneMockValue } from '../../../shared/api/mockResponse'

export const mockUserProfile = {
  userId: 1,
  nickname: '김인송',
  email: 'inseung.kim@committool.com',
  phone: '010-1234-5678',
  profileImage: '',
  sex: 'M',
  age: 20,
  jobFamily: '소프트웨어 개발',
  jobRole: 'Frontend Developer',
  userDescription: '함께 성장하는 프론트엔드 개발자입니다.',
  userColor: '#5f6fe5'
}

export const userMockDatabase = {
  profile: cloneMockValue(mockUserProfile),
  onboardingAnswers: {}
}
