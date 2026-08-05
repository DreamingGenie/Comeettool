const meetingNames = [
  '주간 스프린트 회의',
  '백엔드 API 연동 점검',
  '프론트엔드 UI 리뷰',
  'AI 회의록 품질 검토',
  '배포 준비 회의',
  '팀 일정 조율',
  '실시간 문서 기술 회의',
  '사용자 피드백 리뷰',
  'MVP 기능 점검',
  '인프라 안정화 회의',
  'QA 이슈 트리아지',
  '프로젝트 회고',
  '최종 발표 준비 회의'
]

const speakers = ['김지윤', '김인승', '전진', '채원찬']
const createdAt = (index) =>
  new Date(Date.UTC(2026, 7, 5 - Math.floor(index / 3), 9 + (index % 3), 10 + index)).toISOString()

export const transcriptReports = meetingNames.map((meetingRoomName, index) => ({
  meetingId: 1101 + index,
  meetingRoomName,
  createdAt: createdAt(index),
  transcript: [
    {
      speaker: speakers[index % speakers.length],
      start: 4.2,
      end: 12.8,
      text: `${meetingRoomName}를 시작하겠습니다. 오늘 확인할 안건부터 공유할게요.`
    },
    {
      speaker: speakers[(index + 1) % speakers.length],
      start: 14.1,
      end: 25.6,
      text: '지난 작업의 진행 상황을 확인했고 현재까지 큰 차질은 없습니다.'
    },
    {
      speaker: speakers[(index + 2) % speakers.length],
      start: 28.4,
      end: 41.9,
      text: '남은 작업은 담당자와 완료 목표일을 정해서 액션 아이템으로 관리하겠습니다.'
    },
    {
      speaker: speakers[(index + 3) % speakers.length],
      start: 45.3,
      end: 58.7,
      text: '논의한 내용을 정리한 뒤 다음 회의에서 결과를 다시 확인하겠습니다.'
    }
  ]
}))

export const minutesReports = meetingNames.map((meetingRoomName, index) => ({
  meetingId: 1101 + index,
  meetingRoomName,
  title: `${meetingRoomName} 요약`,
  summary: `${meetingRoomName}에서 현재 진행 상황과 주요 문제를 공유하고 다음 작업의 담당자 및 우선순위를 결정했습니다.`,
  topics: [{ topic: '현재 스프린트 진행 상황 공유' }, { topic: '다음 작업 우선순위 논의' }],
  decisions: [
    { decision: '핵심 기능 검증을 우선 진행한다', owner: speakers[index % speakers.length] },
    {
      decision: '문제가 확인되면 담당자에게 즉시 공유한다',
      owner: speakers[(index + 1) % speakers.length]
    }
  ],
  actionItems: [
    {
      task: '담당 기능 테스트 결과 정리',
      assignee: speakers[(index + 2) % speakers.length],
      dueDate: `2026-08-${String(8 + (index % 8)).padStart(2, '0')}`
    },
    {
      task: '다음 회의 안건 문서화',
      assignee: speakers[(index + 3) % speakers.length],
      dueDate: `2026-08-${String(10 + (index % 8)).padStart(2, '0')}`
    }
  ],
  openIssues:
    index % 3 === 0
      ? [{ issue: '외부 서비스 연결 상태를 추가로 확인해야 함' }]
      : [{ issue: '세부 API 응답 필드 확정 필요' }, { issue: '실사용자 시나리오 테스트 필요' }],
  isConfirmed: index % 4 === 0,
  confirmedAt: index % 4 === 0 ? createdAt(index) : null,
  createdAt: createdAt(index)
}))

export const facilitatorReports = meetingNames.map((meetingRoomName, index) => ({
  meetingId: 1101 + index,
  meetingRoomName,
  title: `${meetingRoomName} 진행 피드백`,
  meetingType: index % 2 === 0 ? '주간 회의' : '기술 회의',
  overallReview:
    '핵심 안건을 중심으로 안정적으로 진행됐으며 후속 작업의 담당자를 더 명확히 지정하면 좋습니다.',
  participationComment:
    '참여자들이 고르게 의견을 공유했지만 일부 논의 구간에서 발언이 한 사람에게 집중되었습니다.',
  participationStats: {
    participantCount: 4,
    activeParticipants: 4,
    balanceScore: 78 + (index % 12)
  },
  qualityEvaluation: {
    score: 82 + (index % 10),
    clarity: '좋음',
    efficiency: index % 3 === 0 ? '개선 필요' : '좋음'
  },
  strengths: [
    '회의 시작 시 목표와 안건을 명확히 공유했습니다.',
    '결정 사항을 발언 내용과 연결해 확인했습니다.'
  ],
  improvements: [
    '각 안건의 논의 종료 시 담당자와 완료 기한을 확인해 주세요.',
    '장시간 이어지는 발언은 핵심 결론을 먼저 말하도록 유도해 주세요.'
  ],
  decisionProcessChecks: [
    { label: '의견 수렴', passed: true },
    { label: '담당자 지정', passed: index % 3 !== 0 }
  ],
  unresolvedIssuesEvaluation: ['외부 API 응답 정책을 백엔드 담당자와 추가 협의해야 합니다.'],
  nextMeetingSuggestions: [
    '미해결 이슈의 진행 상태를 첫 번째 안건으로 확인하세요.',
    '액션 아이템 완료 여부를 회의 전에 공유하세요.'
  ],
  createdAt: createdAt(index)
}))

export const reportMockDatabase = {
  transcriptReports,
  minutesReports,
  facilitatorReports
}
