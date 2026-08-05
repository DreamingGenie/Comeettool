export const workspaces = [
  { id: 'a707', badge: 'A7', name: 'A707', role: 'Owner', members: 7 },
  { id: 'frontend', badge: 'FE', name: 'Frontend', role: 'Member', members: 6 },
  { id: 'product', badge: 'PL', name: 'Product Leader', role: 'Member', members: 5 }
]

export const membersByTeam = {
  a707: [
    ['JW', 'James Wilson', 'james.w@committool.io', 'Admin', 'Active', 'Last active 2 mins ago'],
    ['ER', 'Elena Rodriguez', 'e.rodriguez@committool.io', 'Developer', 'Active', 'Last active 1h ago'],
    ['MT', 'Marcus Thorne', 'm.thorne@committool.io', 'Designer', 'Away', 'Last active 4h ago'],
    ['AK', 'Aisha Khan', 'a.khan@committool.io', 'Developer', 'Offline', 'Yesterday, 18:42'],
    ['SK', 'Sofia Kim', 'sofia.k@committool.io', 'Developer', 'Active', 'Last active 12 mins ago'],
    ['DL', 'Daniel Lee', 'daniel.l@committool.io', 'Designer', 'Away', 'Last active 3h ago'],
    ['MP', 'Mina Park', 'mina.p@committool.io', 'Product Manager', 'Active', 'Last active 28 mins ago']
  ],
  frontend: [
    ['IK', '김인승', 'inseung.kim@committool.io', 'Frontend Lead', 'Active', 'Last active just now'],
    ['HJ', '한지우', 'jiwoo.han@committool.io', 'Frontend Developer', 'Active', 'Last active 8 mins ago'],
    ['SY', '송서윤', 'seoyoon.song@committool.io', 'Frontend Developer', 'Away', 'Last active 1h ago'],
    ['JM', '정민준', 'minjun.jung@committool.io', 'UI Developer', 'Active', 'Last active 24 mins ago'],
    ['YE', '윤예은', 'yeeun.yoon@committool.io', 'Frontend Developer', 'Offline', 'Yesterday, 21:06'],
    ['TH', '최태현', 'taehyun.choi@committool.io', 'QA Engineer', 'Active', 'Last active 32 mins ago']
  ],
  product: [
    ['JY', '이지은', 'jieun.lee@committool.io', 'Product Lead', 'Active', 'Last active 5 mins ago'],
    ['MS', '김민수', 'minsoo.kim@committool.io', 'Product Manager', 'Active', 'Last active 18 mins ago'],
    ['SJ', '박서준', 'seojun.park@committool.io', 'Service Planner', 'Away', 'Last active 2h ago'],
    ['SB', '정수빈', 'subin.jung@committool.io', 'UX Researcher', 'Active', 'Last active 40 mins ago'],
    ['DY', '이도윤', 'doyoon.lee@committool.io', 'Data Analyst', 'Offline', 'Yesterday, 19:31']
  ]
}

const archivePreview = (summary, sections, tags) => ({
  summary,
  sections,
  tags
})

export const archiveData = {
  documents: [
    [
      'API 연동 체크리스트',
      'BE 팀 회의',
      '김인승 외 3명',
      '방금 전',
      '공동 편집',
      archivePreview(
        '프론트엔드와 백엔드 API를 안정적으로 연결하기 위한 공통 점검 문서입니다.',
        [
          {
            title: '연결 환경',
            body: '개발 서버 주소와 CORS 허용 범위를 확인하고 환경 변수별 API 주소를 분리합니다.',
            items: ['Swagger 최신 명세 확인', '로컬 개발 Origin 허용', '환경 변수 노출 여부 점검']
          },
          {
            title: '인증 흐름',
            body: 'Access Token과 Refresh Token의 저장·갱신·만료 흐름을 화면 전환과 함께 검증합니다.',
            items: ['Bearer 헤더 자동 설정', '401 응답 시 토큰 재발급', 'onboarded 값에 따른 경로 분기']
          }
        ],
        ['API', '인증', '배포']
      )
    ],
    [
      '7월 스프린트 백로그',
      '주간 스프린트',
      '김인승 외 7명',
      '18분 전',
      '공동 편집',
      archivePreview(
        '7월 스프린트 목표와 담당자별 진행 항목을 한곳에서 관리합니다.',
        [
          {
            title: '이번 스프린트 목표',
            body: '인증·프로필 API 연동을 안정화하고 회의 생명주기 기능의 프론트 기반을 완성합니다.'
          },
          {
            title: '주요 작업',
            body: '사용자 흐름을 기준으로 우선순위를 조정했습니다.',
            items: ['회원가입 및 로그인 검증', '프로필 조회·수정 연동', '회의 생성·입장 화면 연결']
          }
        ],
        ['Sprint', 'Backlog', 'MVP']
      )
    ],
    [
      '프로젝트 요구사항 v2',
      '서비스 기획 회의',
      '이지은 외 4명',
      '어제',
      '검토 중',
      archivePreview(
        '코밋툴 MVP 범위와 사용자별 핵심 시나리오를 정리한 두 번째 요구사항 문서입니다.',
        [
          {
            title: '핵심 사용자 흐름',
            body: '사용자는 팀 스페이스에서 일정을 확인하고 회의에 참여한 뒤 AI 결과물을 검토합니다.'
          },
          {
            title: '검토 필요 사항',
            body: '초대 권한과 회의 종료 후 결과물 공개 범위를 팀 정책에 맞게 확정해야 합니다.'
          }
        ],
        ['요구사항', '기획', '검토']
      )
    ],
    [
      '배포 전 QA 체크리스트',
      '릴리즈 점검',
      '박서준 외 2명',
      '7월 12일',
      '완료',
      archivePreview(
        '릴리즈 직전 주요 화면과 API 연결 상태를 빠르게 확인하기 위한 QA 문서입니다.',
        [
          {
            title: '기능 점검',
            body: '인증, 라우팅, 반응형 레이아웃과 오류 화면을 브라우저별로 확인했습니다.',
            items: ['로그인 세션 유지', '직접 URL 접근', '빈 데이터 및 서버 오류 상태']
          },
          {
            title: '완료 결과',
            body: '필수 시나리오 테스트를 통과했으며 경미한 UI 개선 항목은 다음 스프린트로 이동했습니다.'
          }
        ],
        ['QA', 'Release', '완료']
      )
    ]
  ],
  minutes: [
    [
      'BE 팀 회의록',
      'BE 팀 회의',
      '참여자 5명 · 52분',
      '7월 15일 14:52',
      '작성 완료',
      archivePreview(
        '백엔드 API 배포 상태와 프론트 연동 일정에 대해 논의한 회의 기록입니다.',
        [
          {
            title: '결정 사항',
            body: 'AUTH API부터 순차 연동하고 Swagger를 단일 계약 기준으로 사용하기로 했습니다.',
            items: ['프로필 필드 제한값 갱신', '오류 응답 코드 정리', '개발 서버 CORS 유지']
          },
          {
            title: '다음 할 일',
            body: '담당자는 변경된 DTO 기준으로 화면 검증 결과를 공유합니다.'
          }
        ],
        ['회의록', 'Backend', 'API']
      )
    ],
    [
      '프론트엔드 코드 리뷰',
      'FE 코드 리뷰',
      '참여자 4명 · 38분',
      '7월 14일 17:08',
      '작성 완료',
      archivePreview(
        '프론트엔드 구조 분리와 Store 경유 원칙을 중심으로 진행한 코드 리뷰입니다.',
        [
          {
            title: '리뷰 요약',
            body: '페이지 컴포넌트의 책임을 줄이고 API 교체 지점을 dataSource로 제한하는 방향에 합의했습니다.'
          },
          {
            title: '개선 항목',
            body: '공통 UI와 도메인 컴포넌트의 경계를 유지합니다.',
            items: ['전역 document 이벤트 제거', '죽은 코드 정리', 'ViewModel 변환 계층 유지']
          }
        ],
        ['회의록', 'Frontend', 'Review']
      )
    ],
    [
      '주간 스프린트 회의록',
      '주간 스프린트',
      '참여자 8명 · 61분',
      '7월 10일 11:01',
      '검토 필요',
      archivePreview(
        '팀 전체 진행 상황과 다음 주 우선순위를 공유한 주간 회의 기록입니다.',
        [
          {
            title: '진행 상황',
            body: 'UI 구현은 주요 화면 기준으로 완료했으며 API 연동 작업을 도메인별로 진행 중입니다.'
          },
          {
            title: '확인 요청',
            body: '회의 API 범위와 AI 결과물 생성 시점을 백엔드 담당자와 다시 확인해야 합니다.'
          }
        ],
        ['회의록', 'Sprint', '검토']
      )
    ]
  ],
  summary: [
    [
      '백엔드 배포 점검 핵심 요약',
      'BE 팀 회의',
      '결정사항 3개 · 할 일 5개',
      '1분 전',
      'AI 생성',
      archivePreview(
        'API 서버 배포는 완료됐으며 프론트 연동 과정에서 DTO 제약과 예외 응답을 우선 확인해야 합니다.',
        [
          {
            title: '핵심 결정',
            body: 'Swagger 최신 문서를 기준으로 AUTH와 USER API를 먼저 연결합니다.',
            items: ['닉네임 최대 20자', '자기소개 최대 255자', '전화번호 하이픈 형식 적용']
          },
          {
            title: '액션 아이템',
            body: '프론트에서는 입력값 검증과 토큰 만료 흐름을 실제 서버 기준으로 재검증합니다.'
          }
        ],
        ['AI 요약', '배포', 'Action Item']
      )
    ],
    [
      '코드 리뷰 결과 요약',
      'FE 코드 리뷰',
      '수정사항 6개 · 할 일 3개',
      '7월 14일',
      'AI 생성',
      archivePreview(
        '도메인별 폴더 분리와 Store 경유 구조가 안정적으로 적용됐으며 일부 UI 검증이 남았습니다.',
        [
          {
            title: '잘된 점',
            body: '라우터, Store, dataSource, mapper의 역할이 분리되어 실제 API 교체 범위가 명확합니다.'
          },
          {
            title: '개선 제안',
            body: '공통 컴포넌트의 접근성과 로딩·오류 상태를 실제 사용자 흐름에서 점검합니다.'
          }
        ],
        ['AI 요약', 'Code Review', 'Frontend']
      )
    ]
  ],
  feedback: [
    [
      '회의 진행 피드백',
      'BE 팀 회의',
      '안건 충실도 92 · 참여 균형 86',
      '1분 전',
      '분석 완료',
      archivePreview(
        '준비된 안건을 대부분 다뤘고 의사결정도 명확했지만 일부 참가자에게 발언이 집중됐습니다.',
        [
          {
            title: '분석 결과',
            body: '안건 충실도 92점, 참여 균형 86점으로 전반적인 회의 품질이 높았습니다.'
          },
          {
            title: '개선 제안',
            body: '다음 회의에서는 안건별로 의견을 묻는 순서를 정해 참여 기회를 고르게 배분해 보세요.'
          }
        ],
        ['AI 피드백', '회의 품질', '참여도']
      )
    ],
    [
      '코드 리뷰 효율 분석',
      'FE 코드 리뷰',
      '결론 도출 88 · 집중도 91',
      '7월 14일',
      '분석 완료',
      archivePreview(
        '리뷰 범위가 명확해 높은 집중도를 유지했으며 대부분의 쟁점에서 실행 가능한 결론을 도출했습니다.',
        [
          {
            title: '분석 결과',
            body: '결론 도출 88점, 집중도 91점으로 기술 논의가 효율적으로 진행됐습니다.'
          },
          {
            title: '개선 제안',
            body: '논의 시작 전에 코드 변경 목적과 확인할 항목을 짧게 공유하면 리뷰 시간을 더 줄일 수 있습니다.'
          }
        ],
        ['AI 피드백', 'Code Review', '효율']
      )
    ]
  ]
}

export const boardSeedData = {
  team: {
    id: 'a707',
    badge: 'A7',
    eyebrow: 'A707 TEAM',
    name: 'A707',
    role: 'Owner',
    memberCount: 7,
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
  membersByTeam,
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
