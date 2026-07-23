const wait = (value, delay = 180) =>
  new Promise(resolve => setTimeout(() => resolve(structuredClone(value)), delay))

export const jobs = ['SW 개발', '기획/프로덕트', '회계/금융', '영업', '마케팅/디자인', '기타']
export const details = ['Frontend', 'Backend', 'Database', 'DevOps', 'AI', 'Mobile']

export const workspaces = [
  { id: 'a707', badge: 'A7', name: 'A707', role: 'Owner', members: 7 },
  { id: 'frontend', badge: 'FE', name: 'Frontend', role: 'Member', members: 6 },
  { id: 'product', badge: 'PL', name: 'Product Leader', role: 'Member', members: 5 }
]

export const members = [
  ['JW', 'James Wilson', 'james.w@committool.io', 'Admin', 'Active', 'Last active 2 mins ago'],
  ['ER', 'Elena Rodriguez', 'e.rodriguez@committool.io', 'Developer', 'Active', 'Last active 1h ago'],
  ['MT', 'Marcus Thorne', 'm.thorne@committool.io', 'Designer', 'Away', 'Last active 4h ago'],
  ['AK', 'Aisha Khan', 'a.khan@committool.io', 'Developer', 'Offline', 'Yesterday, 18:42']
]

export const archiveData = {
  documents: [
    ['API 연동 체크리스트', 'BE 팀 회의', '김인승 외 3명', '방금 전', '공동 편집'],
    ['7월 스프린트 백로그', '주간 스프린트', '김인승 외 7명', '18분 전', '공동 편집'],
    ['프로젝트 요구사항 v2', '서비스 기획 회의', '이지은 외 4명', '어제', '검토 중'],
    ['배포 전 QA 체크리스트', '릴리즈 점검', '박서준 외 2명', '7월 12일', '완료']
  ],
  minutes: [
    ['BE 팀 회의록', 'BE 팀 회의', '참여자 5명 · 52분', '7월 15일 14:52', '작성 완료'],
    ['프론트엔드 코드 리뷰', 'FE 코드 리뷰', '참여자 4명 · 38분', '7월 14일 17:08', '작성 완료'],
    ['주간 스프린트 회의록', '주간 스프린트', '참여자 8명 · 61분', '7월 10일 11:01', '검토 필요']
  ],
  summary: [
    ['백엔드 배포 점검 핵심 요약', 'BE 팀 회의', '결정사항 3개 · 할 일 5개', '1분 전', 'AI 생성'],
    ['코드 리뷰 결과 요약', 'FE 코드 리뷰', '수정사항 6개 · 할 일 3개', '7월 14일', 'AI 생성']
  ],
  feedback: [
    ['회의 진행 피드백', 'BE 팀 회의', '안건 충실도 92 · 참여 균형 86', '1분 전', '분석 완료'],
    ['코드 리뷰 효율 분석', 'FE 코드 리뷰', '결론 도출 88 · 집중도 91', '7월 14일', '분석 완료']
  ]
}

export const boardMockApi = {
  getDashboard: () => wait({ workspaces }),
  getMembers: () => wait(members),
  getArchive: section => wait(archiveData[section] || []),
  createWorkspace: data => wait({ id: crypto.randomUUID(), ...data }),
  createMeeting: data => wait({ id: crypto.randomUUID(), status: 'LIVE', ...data }),
  createEvent: data => wait({ id: crypto.randomUUID(), ...data })
}
