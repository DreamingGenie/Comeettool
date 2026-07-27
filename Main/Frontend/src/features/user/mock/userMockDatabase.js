export const mockUserProfile = {
  id: 1,
  nickname: '김인송',
  email: 'inseung.kim@committool.com',
  phone: '010-1234-5678',
  gender: 'male',
  ageGroup: '20 - 30',
  jobGroup: '소프트웨어 개발',
  job: 'Frontend Developer',
  avatarText: '김'
}

export const profileOptions = {
  genders: [
    { value: 'male', label: '남' },
    { value: 'female', label: '여' }
  ],
  ageGroups: ['20 - 30', '30 - 40'],
  jobGroups: ['소프트웨어 개발', '기획/프로덕트'],
  jobs: ['Frontend Developer', 'Backend Developer']
}

export const onboardingMock = {
  steps: [
    {
      title: '직군을 선택해주세요.',
      label: '직군 선택',
      options: ['SW 개발', '기획/프로덕트', '회계/금융', '영업', '마케팅/디자인', '기타']
    },
    {
      title: '세부 직무를 선택해주세요.',
      label: '직무 선택',
      options: ['Frontend', 'Backend', 'Database', 'DevOps', 'AI', 'Mobile']
    },
    {
      title: '나이와 성별을 알려주세요.',
      label: '나이 / 성별 선택',
      ageOptions: ['10대', '20대', '30대', '40대', '50대', '60대+'],
      genderOptions: [
        { value: 'male', label: '남성' },
        { value: 'female', label: '여성' }
      ]
    }
  ]
}

export const userMockDatabase = {
  profile: structuredClone(mockUserProfile),
  profileOptions,
  onboarding: onboardingMock,
  onboardingAnswers: {}
}
