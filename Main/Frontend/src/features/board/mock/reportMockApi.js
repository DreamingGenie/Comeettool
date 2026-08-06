import { cloneMockValue, mockResponse } from '../../../shared/api/mockResponse'
import {
  toMinutesUpdateRequest,
  toReportDetailViewModel,
  toReportPageViewModel
} from '../mappers/reportMapper'
import { reportMockDatabase } from './reportMockDatabase'

const pageResponse = (items, page, size) => {
  const start = page * size
  const content = items.slice(start, start + size)
  return {
    content,
    page,
    size,
    totalElements: items.length,
    totalPages: Math.ceil(items.length / size),
    hasNext: start + size < items.length
  }
}

const findReport = (items, meetingId) =>
  items.find((item) => String(item.meetingId) === String(meetingId))

const getReportsBySection = (section) =>
  section === 'minutes'
    ? reportMockDatabase.transcriptReports
    : section === 'feedback'
      ? reportMockDatabase.facilitatorReports
      : reportMockDatabase.minutesReports

const createExportResponse = (meetingId, format, body) => {
  const mimeType = format === 'pdf' ? 'application/pdf' : 'text/markdown'
  return mockResponse({
    meetingId: Number(meetingId),
    format,
    url: `data:${mimeType};charset=utf-8,${encodeURIComponent(body)}`
  })
}

export const reportMockApi = {
  getArchive: (_spaceId, section, page = 0, size = 10) => {
    const items = getReportsBySection(section)
    return mockResponse(toReportPageViewModel(pageResponse(items, page, size), section))
  },
  getReportDetail: (meetingId, section, listRow) => {
    const items = getReportsBySection(section)
    const report = findReport(items, meetingId)
    if (!report) return Promise.reject(new Error('목업 보고서를 찾을 수 없습니다.'))
    return mockResponse(toReportDetailViewModel(report, section, listRow))
  },
  updateMinutes: (meetingId, minutes, listRow) => {
    const report = findReport(reportMockDatabase.minutesReports, meetingId)
    if (!report) return Promise.reject(new Error('목업 AI 요약을 찾을 수 없습니다.'))
    if (report.isConfirmed) return Promise.reject(new Error('확정된 AI 요약은 수정할 수 없습니다.'))
    Object.assign(report, cloneMockValue(toMinutesUpdateRequest(minutes)))
    return mockResponse(toReportDetailViewModel(report, 'summary', listRow))
  },
  exportTranscript: (meetingId, format) => {
    const report = findReport(reportMockDatabase.transcriptReports, meetingId)
    if (!report) return Promise.reject(new Error('목업 회의 전문을 찾을 수 없습니다.'))
    const body = report.transcript
      .map((segment) => `${segment.speaker}: ${segment.text}`)
      .join('\n\n')
    return createExportResponse(meetingId, format, body)
  },
  confirmMinutes: (meetingId) => {
    const report = findReport(reportMockDatabase.minutesReports, meetingId)
    if (!report) return Promise.reject(new Error('목업 AI 요약을 찾을 수 없습니다.'))
    report.isConfirmed = true
    report.confirmedAt = new Date().toISOString()
    return mockResponse({
      meetingId: Number(meetingId),
      isConfirmed: true,
      confirmedAt: report.confirmedAt
    })
  },
  exportMinutes: (meetingId, format) => {
    const report = findReport(reportMockDatabase.minutesReports, meetingId)
    if (!report) return Promise.reject(new Error('목업 AI 요약을 찾을 수 없습니다.'))
    if (!report.isConfirmed)
      return Promise.reject(new Error('확정된 AI 요약만 내보낼 수 있습니다.'))
    return createExportResponse(meetingId, format, `# ${report.title}\n\n${report.summary}`)
  },
  exportFacilitatorReport: (meetingId, format) => {
    const report = findReport(reportMockDatabase.facilitatorReports, meetingId)
    if (!report) return Promise.reject(new Error('목업 AI 피드백을 찾을 수 없습니다.'))
    return createExportResponse(
      meetingId,
      format,
      `# ${report.title}\n\n${report.overallReview}\n\n## 참여도\n${report.participationComment}`
    )
  }
}
