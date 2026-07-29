export const genderOptions = [
  { value: 'male', label: '남성' },
  { value: 'female', label: '여성' }
]

export const ageGroupOptions = [
  '10대',
  '20대',
  '30대',
  '40대',
  '50대',
  '60대+'
]

export const jobGroupOptions = [
  'SW 개발',
  '기획/프로덕트',
  '회계/금융',
  '영업',
  '마케팅/디자인',
  '기타'
]

export const jobRolesByGroup = {
  'SW 개발': [
    'Frontend',
    'Backend',
    'Database',
    'DevOps',
    'AI',
    'Mobile'
  ],
  '기획/프로덕트': [
    'Product Manager',
    'Product Owner',
    'Service Planner',
    'Business Analyst',
    'Project Manager',
    'UX Planner'
  ],
  '회계/금융': [
    'Accountant',
    'Financial Analyst',
    'Tax Accountant',
    'Auditor',
    'Treasury Manager',
    'Financial Planner'
  ],
  '영업': [
    'Sales Manager',
    'Account Manager',
    'Sales Operations',
    'Business Development',
    'Customer Success',
    'Technical Sales'
  ],
  '마케팅/디자인': [
    'Brand Marketer',
    'Performance Marketer',
    'Content Marketer',
    'UX Designer',
    'UI Designer',
    'Graphic Designer'
  ],
  '기타': [
    'Human Resources',
    'Recruiter',
    'Legal',
    'General Affairs',
    'Researcher',
    'Student'
  ]
}

export const jobOptions = jobRolesByGroup[jobGroupOptions[0]]

export const profileOptions = {
  genders: genderOptions,
  ageGroups: ageGroupOptions,
  jobGroups: jobGroupOptions,
  jobs: jobOptions,
  jobRolesByGroup,
  colors: [
    { value: '#496FBD', label: '블루' },
    { value: '#6674E8', label: '인디고' },
    { value: '#8B5CF6', label: '퍼플' },
    { value: '#A855D6', label: '바이올렛' },
    { value: '#E85D75', label: '로즈' },
    { value: '#C94A3A', label: '레드' },
    { value: '#F59E42', label: '오렌지' },
    { value: '#2BAF83', label: '그린' },
    { value: '#36A6A1', label: '틸' },
    { value: '#1F2937', label: '차콜' }
  ]
}

export const onboardingOptions = {
  jobRolesByGroup,
  steps: [
    {
      title: '직군을 선택해주세요.',
      label: '직군 선택',
      options: jobGroupOptions
    },
    {
      title: '세부 직무를 선택해주세요.',
      label: '직무 선택',
      options: jobOptions
    },
    {
      title: '나이와 성별을 알려주세요.',
      label: '나이 / 성별 선택',
      ageOptions: ageGroupOptions,
      genderOptions
    }
  ]
}
