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

const facilitatorSingleSpeakerDemoReport = {
  meetingId: 34,
  meetingRoomName: '단일 화자 초단기 세션',
  title: '회의 품질 평가 보고서: 단일 화자 초단기 세션',
  meetingType: null,
  overallReview:
    '세션은 매우 짧고 단일 화자 중심으로 진행되어 목적·아젠다·의사결정 구조가 전혀 드러나지 않았습니다. 초반에 종료 문구로 보이는 멘트가 있어 간결한 종료 신호는 확인되지만, 논의·결정·리캡 절차는 부재했습니다. 발화의 상당 부분이 의성어로 구성되어 메시지 명료도가 낮았고, 운영 역할이나 시간 관리의 흔적도 없었습니다. 과거 보고서에서 반복 지적된 단일 화자 편중과 기본 운영 규칙 부재 문제가 이번에도 동일하게 관찰되었습니다.',
  participationComment:
    '참여 데이터에 따르면 47이 발화 9회, 총 26.5초로 100.0%를 차지했습니다. 따라서 발언 기회가 전적으로 한 명에게 집중되어 참여 균형이 확보되지 않았습니다.',
  participationStats: [
    { ratio: 1.0, speaker: '47', utterance_count: 9, speaking_seconds: 26.5 }
  ],
  qualityEvaluation: {
    agenda_clarity: { grade: '미흡', evidence_timestamp: '00:00:05' },
    time_management: { grade: '미흡', evidence_timestamp: '00:00:29' },
    decision_process: { grade: '미흡', evidence_timestamp: '00:00:29' },
    discussion_focus: { grade: '미흡', evidence_timestamp: '00:00:17' },
    speaking_opportunity_balance: { grade: '미흡', evidence_timestamp: '00:00:05' }
  },
  strengths: [
    {
      content: '짧은 길이 안에서 종료를 알리는 문구가 확인되어 세션 종료 신호는 비교적 명확했음',
      timestamp: '00:00:10'
    }
  ],
  improvements: [
    {
      issue: '아젠다·목적 미선언으로 운영 구조 불명확',
      timestamp: '00:00:05',
      suggestion: '오프닝 60초에 회의 목적(결정/정보공유/아이디어 수집), 기대 산출물, 안건 순서를 한 문단으로 선언하고 화면/문서에 고정하십시오.'
    },
    {
      issue: '발언 기회가 한 명에게 집중됨',
      timestamp: '00:00:29',
      suggestion: '라운드로빈으로 1차 발언 기회를 보장하고, 발언 타이머(예: 60–90초)를 적용해 초기 턴테이킹을 균형화하십시오.'
    },
    {
      issue: '의사결정 방식·기록 부재',
      timestamp: '00:00:29',
      suggestion: '세션 초반에 의사결정 규칙(만장일치/다수결/책임자 결정)을 합의·공유하고, 결정 시 ‘승인/보류/수정’ 상태를 실시간 문서로 기록하십시오.'
    },
    {
      issue: '마무리 리캡 부재',
      timestamp: '00:00:29',
      suggestion: '마지막 60–120초를 고정해 결정 사항, 다음 액션·담당·기한을 구두로 리캡하고 링크를 즉시 공유하십시오.'
    },
    {
      issue: '의성어 중심 발화로 메시지 명료도 저하',
      timestamp: '00:00:17',
      suggestion: '핵심 메시지를 1–2문장으로 사전 스크립트화하고, 무음/의성어 구간은 정리하여 전달하십시오. 필요 시 내레이터/데모 리드를 분리해 설명 품질을 높이십시오.'
    }
  ],
  decisionProcessChecks: [
    {
      decision: '결정事項이 전사에서 확인되지 않음(승인·보류·담당 지정 등 부재)',
      timestamp: '00:00:29',
      consensus_type: '불분명'
    }
  ],
  unresolvedIssuesEvaluation: [
    '회의 목적과 안건이 미정 상태로, 다음 회의 시작 시 최우선으로 합의가 필요함.',
    '의사결정 방식(만장일치/다수결/책임자 결정) 미정—결정 순간의 근거·책임이 남지 않음.',
    '누가 무엇을 언제까지 할지에 대한 액션 아이템·기한이 미확정.',
    '참여 구조(발언 순서·타이머·역할 분담) 부재로 편중 위험 지속.'
  ],
  nextMeetingSuggestions: [
    '사전 아젠다 문서를 공유하고 각 안건의 목표·성공 기준·예상 시간을 명시하십시오.',
    '오프닝 1분에 회의 목적과 의사결정 규칙(만장일치/다수결/책임자 결정)을 선언·기록하십시오.',
    '퍼실리테이터·타임키퍼·서기를 지정해 운영 책임을 분담하십시오.',
    '라운드로빈 1차 발언과 60–90초 발언 타이머를 적용해 초기 참여를 균형화하십시오.',
    '결정/액션 아이템을 실시간으로 기록하고, 종료 1–2분 리캡으로 담당자·기한을 확인하십시오.',
    '비언어·의성어 구간을 줄이기 위해 핵심 메시지 스크립트(한 문단)를 준비하고 화면에 띄우십시오.'
  ],
  createdAt: '2026-08-07T04:06:44.349258Z'
}

const facilitatorMultiSpeakerDemoReport = {
  meetingId: 27,
  title: '디버깅 중심 다자 테스트 세션 품질 평가 보고서',
  meetingType: null,
  overallReview:
    '여러 명이 동시에 테스트하며 오류를 재현·관찰하고, 증거(스크린샷) 수집과 임시 우회책 확인까지 이어진 점은 생산적이었습니다. 다만 회의 목적과 성공 기준, 의사결정 규칙이 선행 공유·선언되지 않아 진행 흐름이 산만해지고 담당·기한이 불명확했습니다. 중후반부에는 교차 발화와 셋업 이슈로 시간이 소모되었고, 종료 시 리캡 없이 “끝”으로 마무리되어 합의·다음 단계가 명료하게 남지 않았습니다. 과거의 단일 화자 중심 패턴과 달리 이번에는 6명이 모두 발화해 참여 균형은 개선된 점이 긍정적입니다.',
  participationComment:
    '발화 데이터에 따르면 27이 171회/629.3초로 25.1%로 가장 많았고, 28이 96회/466.6초로 18.6%, 32가 113회/447.0초로 17.8%, 29가 159회/412.2초로 16.5%, 31이 88회/339.6초로 13.6%, 30이 58회/210.3초로 8.4%였습니다. 6명 모두 발화했으나 상위 화자(27)와 하위 화자(30) 사이의 비중 격차가 존재합니다. 다음 회의에서는 라운드로빈 1차 발언과 타임드 토크(예: 60–90초)로 30의 발언 비중을 확대하고, 27은 요약·촉진 위주로 전환해 균형을 보완하는 것을 권장합니다.',
  participationStats: [
    { ratio: 0.2512325890558068, speaker: '27', utterance_count: 171, speaking_seconds: 629.31 },
    { ratio: 0.18625568388232616, speaker: '28', utterance_count: 96, speaking_seconds: 466.55000000000007 },
    { ratio: 0.17843498117681814, speaker: '32', utterance_count: 113, speaking_seconds: 446.96000000000004 },
    { ratio: 0.16456211650012575, speaker: '29', utterance_count: 159, speaking_seconds: 412.21000000000004 },
    { ratio: 0.13555485470419856, speaker: '31', utterance_count: 88, speaking_seconds: 339.55 },
    { ratio: 0.08395977468072449, speaker: '30', utterance_count: 58, speaking_seconds: 210.31 }
  ],
  qualityEvaluation: {
    agenda_clarity: { grade: '미흡', evidence_timestamp: '00:02:01' },
    time_management: { grade: '미흡', evidence_timestamp: '00:11:05' },
    decision_process: { grade: '미흡', evidence_timestamp: '00:10:56' },
    discussion_focus: { grade: '보통', evidence_timestamp: '00:03:27' },
    speaking_opportunity_balance: { grade: '보통', evidence_timestamp: '00:03:33' }
  },
  strengths: [
    {
      content: '오류 상황을 즉시 캡처하자는 제안으로 증거 기반 문제 해결 흐름을 만들었음(“스샷 먼저 찍으시죠”)',
      timestamp: '00:03:27'
    },
    {
      content: '카메라 멈춤 이슈에 대해 빠르게 재현·우회책(껐다 켜기)을 확인하며 실험-피드백 루프를 짧게 유지함',
      timestamp: '00:04:10'
    },
    {
      content: '종료 시 명확한 구두 신호(“끝”)로 회의 종료를 알림',
      timestamp: '00:11:05'
    }
  ],
  improvements: [
    {
      issue: '목적·아젠다 미선언으로 초기 정렬 부재—오프닝 2분에 이번 세션의 목표(예: 특정 버그 재현/원인 규명/다음 액션 결정)와 성공 기준, 안건 타임박스를 명시하십시오.',
      timestamp: '00:02:01',
      suggestion: '회의 시작 시 목적/성공기준/의제별 타임박스를 한 화면으로 공유하고 참가자 동의를 받으십시오.'
    },
    {
      issue: '의사결정·담당자·기한이 불명확—팔로업을 말로만 합의하고 책임·기한을 못 박지 않음.',
      timestamp: '00:10:56',
      suggestion: '결정 순간에 RACI(또는 담당자/기한) 필드를 실시간 문서에 기입하고, 마무리 2분에 재확인·낭독하십시오.'
    },
    {
      issue: '시간관리(타임키핑) 부재로 셋업·트러블슈팅에 시간이 과다 소요됨.',
      timestamp: '00:00:50',
      suggestion: '타임키퍼를 지정하고 안건별 타임박스를 설정(예: 셋업 2분 한정)하여 초과 시 스레드 이동 또는 비동기 처리로 전환하십시오.'
    },
    {
      issue: '교차 발화·동시 발화로 인지 부하 증가(여러 사람이 동시에 말하며 발화가 겹침).',
      timestamp: '00:03:33',
      suggestion: '핸드 레이즈·발언 큐 또는 라운드로빈을 적용하고, 퍼실리테이터가 호명을 통해 발언 순서를 관리하십시오.'
    },
    {
      issue: '종료 리캡 부재—결정·담당·기한이 문서에 남지 않음.',
      timestamp: '00:11:05',
      suggestion: '마무리 2분을 고정하여 결정사항/담당자/기한/리스크를 낭독하고, 회의 직후 링크를 전원에게 공유하십시오.'
    }
  ],
  decisionProcessChecks: [
    {
      decision: '에러 재현 화면을 우선 스크린샷으로 수집하기로 함(참가자 동의 반응 존재).',
      timestamp: '00:03:27',
      consensus_type: '불분명'
    },
    {
      decision: '세션 중 발생한 문제를 정리해 공유·후속 검토하기로 함(“보내주시면 될 것 같습니다”).',
      timestamp: '00:11:00',
      consensus_type: '불분명'
    }
  ],
  unresolvedIssuesEvaluation: [
    '페이지 전환 시 카메라 멈춤의 근본 원인 미확정—임시 우회(재시작)만 확인됨. 다음 회의에서 재현 조건 정의→로그 수집→원인 가설 검증을 최우선으로.',
    'VAD 업로드 실패/40401 등 에러 코드의 원인·범위·영향 미정—오류 매트릭스(발생 조건/빈도/영향/우회책) 작성과 담당 지정이 필요.',
    '성능 저하(“트래픽이 계속 쌓이는 듯, 점점 느려짐”)의 지표·재현 경로 부재—클라이언트/네트워크/서버 측 지표를 사전 계측하고 부하 프로파일링 우선순위 상향.',
    '버전 관리/업데이트 동작의 범위·일정 불명확—기능 정의와 배포 캘린더 합의 필요.'
  ],
  nextMeetingSuggestions: [
    '사전 아젠다 문서를 배포하고 오프닝 2분에 목적·성공 기준·의사결정 규칙(만장/다수/책임자 결정)을 선언하십시오.',
    '퍼실리테이터·타임키퍼·서기를 지정하고, 안건별 타임박스(예: 오류 재현 5분, 원인 가설 5분, 액션 합의 3분)를 공지하십시오.',
    '공유 이슈 트래커(템플릿: 제목/재현 경로/로그·스샷 링크/담당/기한/우선순위)를 회의 시작과 동시에 열고 실시간으로 기록·화면 공유하십시오.',
    '동시 발화 방지를 위해 핸드 레이즈 또는 라운드로빈 1차 발언을 적용하고, 발언 타이머(60–90초)로 상위 화자의 점유율을 제어하십시오.',
    '재현 실험은 ‘실험 설계→실행→관찰/로그→다음 가설’ 체크리스트로 진행하고, 각 실험마다 기록 담당을 배정하십시오.',
    '마무리 2분에 결정·담당자·기한을 낭독하고, 회의 종료 직후 요약 링크를 채널에 게시하십시오.',
    '테스트 전 사전 셋업 체크리스트(접속/권한/카메라/화면공유)를 사용해 셋업 시간을 2분 이내로 제한하십시오.'
  ],
  createdAt: '2026-08-07T01:37:44.111789Z'
}

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
})).map((report, index) => {
  if (index === 0) return facilitatorSingleSpeakerDemoReport
  if (index === 1) return facilitatorMultiSpeakerDemoReport
  return report
})

export const reportMockDatabase = {
  transcriptReports,
  minutesReports,
  facilitatorReports
}
