import { describe, expect, it } from 'vitest'
import { toMinutesUpdateRequest, toReportDetailViewModel } from './reportMapper'

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
