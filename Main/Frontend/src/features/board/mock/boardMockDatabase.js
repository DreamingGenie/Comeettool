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

export const boardSeedData = {
  team: {
    id: 'a707',
    badge: 'A7',
    eyebrow: 'A707 TEAM',
    name: 'A707',
    role: 'Owner',
    memberCount: 8,
    description: '화상회의 협업 플랫폼을 함께 만드는 프로젝트 팀입니다.',
    colorOptions: ['Commit Blue', 'Emerald', 'Orange'],
    defaultMemberRoles: ['Member', 'Guest', 'Admin'],
    color: 'Commit Blue',
    defaultMemberRole: 'Member',
    inviteLinkEnabled: true,
    ownerApprovalRequired: false,
    notifications: {
      meetingReminder: true,
      documentUpdates: true,
      aiSummary: true,
      weeklyReport: false
    }
  },
  activeMeeting: {
    id: 'be-team-meeting',
    title: 'BE 팀 회의',
    roomTitle: '주간 기획 회의',
    status: 'LIVE',
    description: '백엔드 API 연동 및 배포 점검',
    date: '7월 15일',
    time: '14:00',
    agendaTime: '오후 2:30',
    participantCount: 5,
    avatars: ['SJ', 'JY', '+3']
  },
  calendar: {
    year: 2026,
    month: 7,
    leadingBlankDays: 3,
    totalDays: 31,
    weekdays: ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'],
    events: [
      { day: 7, title: '데일리 스크럼', tone: 'default' },
      { day: 14, title: '데일리 스크럼', tone: 'default' },
      { day: 14, title: '코드 리뷰', tone: 'green' },
      { day: 16, title: '중간 발표', tone: 'orange' }
    ]
  },
  meetingRoom: {
    totalParticipants: 12,
    participants: [
      { id: 'me', name: '홍길동', displayName: '나 (홍길동)', avatarText: '나', role: '나', status: '회의 참여 중', isMe: true, muted: false },
      { id: 'leader', name: '이지은 팀장', displayName: '이지은 팀장', avatarText: '이', role: '참가자', status: '회의 참여 중', muted: false },
      { id: 'manager', name: '김민수 매니저', displayName: '김민수 매니저', avatarText: 'KM', role: '참가자', status: '회의 참여 중', muted: true },
      { id: 'developer', name: '박서준', displayName: '박서준', avatarText: '박서준', role: '참가자', status: '회의 참여 중', muted: false },
      { id: 'member-5', name: '정수빈', displayName: '정수빈', avatarText: '정', role: '참가자', status: '온라인', muted: false },
      { id: 'member-6', name: '이도윤', displayName: '이도윤', avatarText: '이', role: '참가자', status: '온라인', muted: false }
    ],
    chatMessages: [
      { id: 1, sender: '이지은 팀장', time: '2:15', body: '오늘 회의 자료 모두 확인하셨나요?', mine: false },
      { id: 2, sender: '나', time: '2:16', body: '네, 3페이지 수정사항 반영해서 업로드했습니다.', mine: true },
      { id: 3, sender: '김민수 매니저', time: '2:18', body: '확인했습니다. 감사합니다!', mine: false }
    ],
    directContacts: [
      {
        id: 'leader',
        name: '이지은 팀장',
        avatarText: '이',
        time: '방금 전',
        preview: '오늘 회의 자료 확인 부탁드려요.',
        messages: [
          { sender: '이지은 팀장', time: '방금', body: '안녕하세요! 회의 관련해서 따로 전달드릴 내용이 있어요.', mine: false },
          { sender: '나', time: '방금', body: '네, 확인했습니다. 말씀해주세요.', mine: true }
        ]
      },
      {
        id: 'manager',
        name: '김민수 매니저',
        avatarText: '김',
        time: '5분 전',
        preview: '배포 일정 공유드렸습니다.',
        messages: [
          { sender: '김민수 매니저', time: '5분 전', body: '배포 일정 공유드렸습니다. 확인 부탁드립니다.', mine: false },
          { sender: '나', time: '방금', body: '확인하고 의견 남기겠습니다.', mine: true }
        ]
      },
      {
        id: 'developer',
        name: '박서준',
        avatarText: '박',
        time: '12분 전',
        preview: '코드 리뷰 완료했습니다.',
        messages: [
          { sender: '박서준', time: '12분 전', body: '요청하신 코드 리뷰 완료했습니다.', mine: false },
          { sender: '나', time: '방금', body: '감사합니다. 수정사항 반영할게요.', mine: true }
        ]
      }
    ]
  },
  inviteMembers: [
    { id: 1, name: '김지수 (나)', email: 'jisoo.kim@committool.com', avatarText: '김', role: 'OWNER' },
    { id: 2, name: '박민호', email: 'minho.park@committool.com', avatarText: '박', role: 'MEMBER' },
    { id: 3, name: '이지은', email: 'jieun.lee@committool.com', avatarText: '이', role: 'MEMBER' },
    { id: 4, name: 'Robert Ford', email: 'robert.ford@committool.com', avatarText: 'R', role: 'MEMBER' }
  ]
}

export const archiveStats = {
  documents: {
    total: 4,
    weeklyChange: 3,
    secondaryLabel: '최근 업데이트',
    secondaryValue: '오늘',
    secondaryDetail: '6개 변경됨',
    tertiaryLabel: '팀 공유',
    tertiaryValue: '8명',
    tertiaryDetail: '모든 멤버에게 공개'
  },
  minutes: {
    total: 3,
    weeklyChange: 2,
    secondaryLabel: '최근 업데이트',
    secondaryValue: '오늘',
    secondaryDetail: '3개 변경됨',
    tertiaryLabel: '팀 공유',
    tertiaryValue: '8명',
    tertiaryDetail: '모든 멤버에게 공개'
  },
  summary: {
    total: 2,
    weeklyChange: 2,
    secondaryLabel: '최근 업데이트',
    secondaryValue: '오늘',
    secondaryDetail: '2개 변경됨',
    tertiaryLabel: '완료된 액션',
    tertiaryValue: '14',
    tertiaryDetail: '진행 중 7개'
  },
  feedback: {
    total: 2,
    weeklyChange: 2,
    secondaryLabel: '평균 참여도',
    secondaryValue: '87%',
    secondaryDetail: '지난주 대비 +4%',
    tertiaryLabel: '팀 공유',
    tertiaryValue: '8명',
    tertiaryDetail: '모든 멤버에게 공개'
  }
}

export const boardMockDatabase = {
  workspaces,
  members,
  archives: archiveData,
  archiveStats,
  teams: workspaces.map((workspace, index) =>
    index === 0
      ? boardSeedData.team
      : {
          ...boardSeedData.team,
          id: workspace.id,
          badge: workspace.badge,
          eyebrow: `${workspace.name.toUpperCase()} TEAM`,
          name: workspace.name,
          role: workspace.role,
          memberCount: workspace.members
        }
  ),
  activeMeetings: [boardSeedData.activeMeeting],
  calendars: { [boardSeedData.team.id]: boardSeedData.calendar },
  meetingRooms: { [boardSeedData.activeMeeting.id]: boardSeedData.meetingRoom },
  inviteMembers: { [boardSeedData.team.id]: boardSeedData.inviteMembers }
}
