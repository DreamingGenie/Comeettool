import { describe, expect, it } from 'vitest'
import {
  normalizeFacilitatorReport,
  toMinutesUpdateRequest,
  toReportDetailViewModel
} from './reportMapper'

const actualMinutesResponse = {
  meetingId: 2,
  title: '파일 업로드 기능 현황 점검 및 관련 이슈',
  summary: '파일 업로드 기능의 동작 여부와 후속 작업을 논의했다.',
  topics: [
    {
      title: '파일 업로드 기능 존재 여부 확인',
      summary: '로컬 환경과 배포 환경의 기능 제공 범위를 확인한다.'
    }
  ],
  decisions: [],
  actionItems: [
    {
      task: '파일 업로드 기능의 존재/동작 여부 확인',
      assignee: null,
      due_date: null,
      source_timestamp: '00:00:19'
    }
  ],
  openIssues: ['파일 업로드 기능의 환경별 제공 범위 및 배포 상태 불명확'],
  isConfirmed: true,
  confirmedAt: '2026-08-05T07:07:04.39174Z',
  createdAt: '2026-08-05T07:03:26.016774Z'
}

describe('reportMapper AI 회의 요약', () => {
  it('유동적인 AI 응답을 화면에서 사용하는 필드로 정규화한다', () => {
    const report = toReportDetailViewModel(actualMinutesResponse, 'summary')

    expect(report.topics[0]).toMatchObject({
      title: '파일 업로드 기능 존재 여부 확인',
      summary: '로컬 환경과 배포 환경의 기능 제공 범위를 확인한다.'
    })
    expect(report.actionItems[0]).toMatchObject({
      task: '파일 업로드 기능의 존재/동작 여부 확인',
      assignee: '',
      dueDate: '',
      sourceTimestamp: '00:00:19'
    })
    expect(report.openIssues[0].issue).toBe(
      '파일 업로드 기능의 환경별 제공 범위 및 배포 상태 불명확'
    )
    expect(report.confirmed).toBe(true)
  })

  it('수정 데이터를 백엔드 JSON 형식으로 직렬화한다', () => {
    const report = toReportDetailViewModel(actualMinutesResponse, 'summary')
    const request = toMinutesUpdateRequest(report)

    expect(request.topics[0]).toEqual({
      title: '파일 업로드 기능 존재 여부 확인',
      summary: '로컬 환경과 배포 환경의 기능 제공 범위를 확인한다.'
    })
    expect(request.actionItems[0]).toEqual({
      task: '파일 업로드 기능의 존재/동작 여부 확인',
      assignee: null,
      due_date: null,
      source_timestamp: '00:00:19'
    })
    expect(request.openIssues).toEqual(['파일 업로드 기능의 환경별 제공 범위 및 배포 상태 불명확'])
  })
})

describe('reportMapper AI 회의 피드백', () => {
  it('snake_case, 중첩 객체, JSON 문자열과 래퍼 배열을 표준 카드에 배치한다', () => {
    const response = {
      meeting_id: 31,
      result: {
        report_title: '유동 응답 테스트',
        meeting_type: 'RETROSPECTIVE',
        overall_feedback: '전체적으로 안정적인 회의였습니다.',
        participation_summary: '참여가 고르게 이루어졌습니다.',
        participation_metrics: '{"participant_count":5,"balance_score":91}',
        good_points: ['목표가 명확했습니다.'],
        areas_for_improvement: { items: ['시간 배분을 개선하세요.'] },
        decision_checks: [{ label: '담당자 지정', passed: true }],
        open_issues: { list: ['배포 일정 확인'] },
        recommendations: ['다음 회의에서 배포 일정을 먼저 확인하세요.']
      }
    }

    const report = toReportDetailViewModel(response, 'feedback')

    expect(report).toMatchObject({
      id: 31,
      title: '유동 응답 테스트',
      meetingType: 'RETROSPECTIVE',
      overallReview: '전체적으로 안정적인 회의였습니다.',
      participationComment: '참여가 고르게 이루어졌습니다.',
      participationStats: { participant_count: 5, balance_score: 91 },
      strengths: ['목표가 명확했습니다.'],
      improvements: ['시간 배분을 개선하세요.'],
      unresolvedIssuesEvaluation: ['배포 일정 확인'],
      nextMeetingSuggestions: ['다음 회의에서 배포 일정을 먼저 확인하세요.']
    })
  })

  it('정해지지 않은 최상위 항목과 sections 배열을 추가 카드로 보존한다', () => {
    const normalized = normalizeFacilitatorReport({
      strengths: [],
      risk_signals: { level: '주의', reasons: ['일정 지연 가능성'] },
      sections: [
        { key: 'speaker_flow', label: '발언 흐름', content: ['중간 끊김이 적었습니다.'] }
      ]
    })

    expect(normalized.additionalFeedbackSections).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ key: 'risk_signals' }),
        expect.objectContaining({ key: 'speaker_flow', label: '발언 흐름' })
      ])
    )
  })
})
