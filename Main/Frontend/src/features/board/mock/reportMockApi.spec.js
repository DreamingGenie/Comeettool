import { describe, expect, it } from 'vitest'
import { reportMockApi } from './reportMockApi'
import { reportMockDatabase } from './reportMockDatabase'

const sections = [
  ['minutes', 'transcriptReports', 'transcript'],
  ['summary', 'minutesReports', 'minutes'],
  ['feedback', 'facilitatorReports', 'facilitator']
]

describe('reportMockApi', () => {
  it.each(sections)('%s 목업을 13개 제공한다', async (section, databaseKey) => {
    const firstPage = await reportMockApi.getArchive('space-1', section, 0, 10)
    const secondPage = await reportMockApi.getArchive('space-1', section, 1, 10)

    expect(reportMockDatabase[databaseKey]).toHaveLength(13)
    expect(firstPage.rows).toHaveLength(10)
    expect(secondPage.rows).toHaveLength(3)
    expect(firstPage.pagination).toMatchObject({
      page: 0,
      size: 10,
      totalElements: 13,
      totalPages: 2,
      hasNext: true
    })
  })

  it.each(sections)('%s 두 번째 페이지 항목의 상세를 조회한다', async (section, _databaseKey, kind) => {
    const page = await reportMockApi.getArchive('space-1', section, 1, 10)
    const row = page.rows[0]
    const detail = await reportMockApi.getReportDetail(row.id, section, row)

    expect(detail.id).toBe(row.id)
    expect(detail.kind).toBe(kind)
  })
})
