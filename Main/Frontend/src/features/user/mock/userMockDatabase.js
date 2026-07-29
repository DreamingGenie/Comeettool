import { cloneMockValue } from '../../../shared/api/mockResponse'
import {
  onboardingOptions,
  profileOptions
} from '../constants/profileOptions'

export const mockUserProfile = {
  id: 1,
  nickname: '김인송',
  email: 'inseung.kim@committool.com',
  phone: '010-1234-5678',
  gender: 'male',
  ageGroup: '20대',
  jobGroup: 'SW 개발',
  job: 'Frontend',
  bio: '팀원들과 즐겁게 협업하는 프론트엔드 개발자입니다.',
  color: '#496FBD',
  avatarText: '김',
  onboarded: false
}

export const userMockDatabase = {
  profile: cloneMockValue(mockUserProfile),
  profileOptions,
  onboarding: onboardingOptions,
  onboardingAnswers: {}
}
