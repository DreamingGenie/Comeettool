const formatReportDate = (value) => {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

const parseJsonValue = (value) => {
  if (value == null || value === '') return []
  if (typeof value !== 'string') return value
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}

const asArray = (value) => {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) return parsed
  if (Array.isArray(parsed?.segments)) return parsed.segments
  if (Array.isArray(parsed?.utterances)) return parsed.utterances
  return parsed ? [parsed] : []
}

const toTranscriptRow = (report) => ({
  id: report?.meetingId,
  kind: 'transcript',
  title: report?.meetingRoomName || '제목 없는 회의',
  meetingName: report?.meetingRoomName || '',
  updatedAt: formatReportDate(report?.createdAt),
  confirmed: null
})

const toMinutesRow = (report) => ({
  id: report?.meetingId,
  kind: 'minutes',
  title: report?.title || report?.meetingRoomName || '제목 없는 AI 요약',
  meetingName: report?.meetingRoomName || '',
  updatedAt: formatReportDate(report?.createdAt),
  confirmed: Boolean(report?.isConfirmed)
})

const toFacilitatorRow = (report) => ({
  id: report?.meetingId,
  kind: 'facilitator',
  title: report?.title || report?.meetingRoomName || '제목 없는 AI 피드백',
  meetingName: report?.meetingRoomName || '',
  meetingType: report?.meetingType || '',
  updatedAt: formatReportDate(report?.createdAt),
  confirmed: null
})

export const toReportPageViewModel = (response, section) => {
  const content = Array.isArray(response?.content) ? response.content : []
  const toRow =
    section === 'minutes'
      ? toTranscriptRow
      : section === 'feedback'
        ? toFacilitatorRow
        : toMinutesRow
  return {
    rows: content.map(toRow),
    pagination: {
      page: Number(response?.page ?? 0),
      size: Number(response?.size ?? 10),
      totalElements: Number(response?.totalElements ?? content.length),
      totalPages: Math.max(1, Number(response?.totalPages ?? 1)),
      hasNext: Boolean(response?.hasNext)
    }
  }
}

const normalizeTranscript = (transcript) =>
  asArray(transcript)
    .map((segment, index) =>
      typeof segment === 'string'
        ? { id: index, speaker: '발화자', start: null, end: null, text: segment }
        : {
            ...segment,
            id: segment?.id ?? index,
            speaker:
              segment?.speaker || segment?.speakerName || segment?.participantName || '발화자',
            start: Number.isFinite(Number(segment?.start)) ? Number(segment.start) : null,
            end: Number.isFinite(Number(segment?.end)) ? Number(segment.end) : null,
            text: segment?.text || segment?.content || ''
          }
    )
    .sort((a, b) => (a.start ?? Number.MAX_SAFE_INTEGER) - (b.start ?? Number.MAX_SAFE_INTEGER))

const firstValue = (...values) => values.find((value) => value !== undefined && value !== null)

const normalizeItem = (item, index, aliases) => {
  const source = typeof item === 'string' ? { [aliases.primary]: item } : item || {}
  const normalized = { ...source, _clientId: `item-${index}` }

  Object.entries(aliases.fields).forEach(([target, candidates]) => {
    normalized[target] = firstValue(...candidates.map((key) => source[key]), '')
  })

  return normalized
}

const normalizeItems = (value, aliases) =>
  asArray(value).map((item, index) => normalizeItem(item, index, aliases))

const itemAliases = {
  topics: {
    primary: 'title',
    fields: {
      title: ['title', 'topic', 'name'],
      summary: ['summary', 'description', 'content'],
      sourceTimestamp: ['sourceTimestamp', 'source_timestamp', 'timestamp']
    }
  },
  decisions: {
    primary: 'decision',
    fields: {
      decision: ['decision', 'title', 'content', 'summary'],
      rationale: ['rationale', 'reason'],
      owner: ['owner', 'assignee'],
      sourceTimestamp: ['sourceTimestamp', 'source_timestamp', 'timestamp']
    }
  },
  actionItems: {
    primary: 'task',
    fields: {
      task: ['task', 'title', 'content'],
      assignee: ['assignee', 'owner'],
      dueDate: ['dueDate', 'due_date'],
      sourceTimestamp: ['sourceTimestamp', 'source_timestamp', 'timestamp']
    }
  },
  openIssues: {
    primary: 'issue',
    fields: {
      issue: ['issue', 'title', 'content', 'description'],
      sourceTimestamp: ['sourceTimestamp', 'source_timestamp', 'timestamp']
    }
  }
}

export const toReportDetailViewModel = (response, section, listRow = {}) => {
  if (section === 'minutes') {
    return {
      ...listRow,
      id: response?.meetingId ?? listRow.id,
      kind: 'transcript',
      createdAt: response?.createdAt,
      updatedAt: formatReportDate(response?.createdAt || listRow.updatedAt),
      transcript: normalizeTranscript(response?.transcript)
    }
  }

  if (section === 'feedback') {
    return {
      ...listRow,
      id: response?.meetingId ?? listRow.id,
      kind: 'facilitator',
      title: response?.title || listRow.title,
      meetingType: response?.meetingType || listRow.meetingType || '',
      overallReview: response?.overallReview || '',
      participationComment: response?.participationComment || '',
      participationStats: parseJsonValue(response?.participationStats),
      qualityEvaluation: parseJsonValue(response?.qualityEvaluation),
      strengths: parseJsonValue(response?.strengths),
      improvements: parseJsonValue(response?.improvements),
      decisionProcessChecks: parseJsonValue(response?.decisionProcessChecks),
      unresolvedIssuesEvaluation: parseJsonValue(response?.unresolvedIssuesEvaluation),
      nextMeetingSuggestions: parseJsonValue(response?.nextMeetingSuggestions),
      createdAt: response?.createdAt,
      updatedAt: formatReportDate(response?.createdAt || listRow.updatedAt)
    }
  }

  return {
    ...listRow,
    id: response?.meetingId ?? listRow.id,
    kind: 'minutes',
    title: response?.title || listRow.title,
    summary: response?.summary || '',
    topics: normalizeItems(response?.topics, itemAliases.topics),
    decisions: normalizeItems(response?.decisions, itemAliases.decisions),
    actionItems: normalizeItems(response?.actionItems, itemAliases.actionItems),
    openIssues: normalizeItems(response?.openIssues, itemAliases.openIssues),
    confirmed: Boolean(response?.isConfirmed),
    confirmedAt: response?.confirmedAt,
    createdAt: response?.createdAt,
    updatedAt: formatReportDate(response?.createdAt || listRow.updatedAt)
  }
}

const cleanText = (value) => {
  if (value === undefined || value === null) return null
  const text = String(value).trim()
  return text || null
}

const withoutAliases = (item, aliases) => {
  const clone = { ...item }
  ;['_clientId', ...aliases].forEach((key) => delete clone[key])
  return clone
}

const serializeTopics = (items) =>
  items.map((item) => ({
    ...withoutAliases(item, [
      'topic',
      'name',
      'description',
      'content',
      'sourceTimestamp',
      'source_timestamp',
      'timestamp'
    ]),
    title: cleanText(item.title) || '',
    summary: cleanText(item.summary) || '',
    ...(cleanText(item.sourceTimestamp)
      ? { source_timestamp: cleanText(item.sourceTimestamp) }
      : {})
  }))

const serializeDecisions = (items) =>
  items.map((item) => ({
    ...withoutAliases(item, [
      'title',
      'content',
      'summary',
      'reason',
      'assignee',
      'sourceTimestamp',
      'source_timestamp',
      'timestamp'
    ]),
    decision: cleanText(item.decision) || '',
    ...(cleanText(item.rationale) ? { rationale: cleanText(item.rationale) } : {}),
    ...(cleanText(item.owner) ? { owner: cleanText(item.owner) } : {}),
    ...(cleanText(item.sourceTimestamp)
      ? { source_timestamp: cleanText(item.sourceTimestamp) }
      : {})
  }))

const serializeActionItems = (items) =>
  items.map((item) => ({
    ...withoutAliases(item, [
      'title',
      'content',
      'owner',
      'dueDate',
      'due_date',
      'sourceTimestamp',
      'source_timestamp',
      'timestamp'
    ]),
    task: cleanText(item.task) || '',
    assignee: cleanText(item.assignee),
    due_date: cleanText(item.dueDate),
    source_timestamp: cleanText(item.sourceTimestamp)
  }))

const serializeOpenIssues = (items) =>
  items.map((item) => {
    const issue = cleanText(item.issue) || ''
    const sourceTimestamp = cleanText(item.sourceTimestamp)
    return sourceTimestamp ? { issue, source_timestamp: sourceTimestamp } : issue
  })

export const toMinutesUpdateRequest = (minutes) => ({
  title: String(minutes.title || '').trim(),
  summary: String(minutes.summary || '').trim(),
  topics: serializeTopics(minutes.topics || []),
  decisions: serializeDecisions(minutes.decisions || []),
  actionItems: serializeActionItems(minutes.actionItems || []),
  openIssues: serializeOpenIssues(minutes.openIssues || [])
})
