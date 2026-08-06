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

const normalizeKey = (key) =>
  String(key || '')
    .replace(/([a-z0-9])([A-Z])/g, '$1_$2')
    .replace(/[\s-]+/g, '_')
    .replace(/[^\p{L}\p{N}_]/gu, '')
    .toLowerCase()

const normalizeDynamicValue = (value) => {
  const parsed = parseJsonValue(value)
  if (Array.isArray(parsed)) return parsed.map(normalizeDynamicValue)
  if (!parsed || typeof parsed !== 'object') return parsed

  const entries = Object.entries(parsed)
  if (entries.length === 1) {
    const [key, wrapped] = entries[0]
    if (['items', 'list', 'values', 'content', 'entries'].includes(normalizeKey(key))) {
      return normalizeDynamicValue(wrapped)
    }
  }

  return Object.fromEntries(entries.map(([key, item]) => [key, normalizeDynamicValue(item)]))
}

const collectObjectScopes = (source, depth = 0, scopes = []) => {
  if (!source || typeof source !== 'object' || Array.isArray(source) || depth > 3) return scopes
  scopes.push(source)
  Object.values(source).forEach((value) => {
    const parsed = parseJsonValue(value)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
      collectObjectScopes(parsed, depth + 1, scopes)
    }
  })
  return scopes
}

const findAliasedValue = (scopes, aliases) => {
  const normalizedAliases = new Set(aliases.map(normalizeKey))
  for (const scope of scopes) {
    const match = Object.entries(scope).find(([key]) => normalizedAliases.has(normalizeKey(key)))
    if (match && match[1] !== undefined && match[1] !== null) return match[1]
  }
  return undefined
}

const facilitatorFields = {
  title: ['title', 'reportTitle', 'report_title', 'feedbackTitle', 'feedback_title', '제목'],
  meetingType: ['meetingType', 'meeting_type', 'type', '회의유형'],
  overallReview: [
    'overallReview',
    'overall_review',
    'overallFeedback',
    'overall_feedback',
    'review',
    'summary',
    '총평',
    '종합의견'
  ],
  participationComment: [
    'participationComment',
    'participation_comment',
    'participationReview',
    'participation_review',
    'participationSummary',
    'participation_summary',
    '참여종합의견'
  ],
  participationStats: [
    'participationStats',
    'participation_stats',
    'participationMetrics',
    'participation_metrics',
    'participation',
    '참여통계'
  ],
  qualityEvaluation: [
    'qualityEvaluation',
    'quality_evaluation',
    'meetingQuality',
    'meeting_quality',
    'quality',
    '회의품질평가'
  ],
  strengths: ['strengths', 'goodPoints', 'good_points', 'positives', 'wellDone', 'well_done', '잘된점'],
  improvements: [
    'improvements',
    'improvementPoints',
    'improvement_points',
    'areasForImprovement',
    'areas_for_improvement',
    'weaknesses',
    '개선할점'
  ],
  decisionProcessChecks: [
    'decisionProcessChecks',
    'decision_process_checks',
    'decisionChecks',
    'decision_checks',
    'decisionProcess',
    'decision_process',
    '의사결정과정'
  ],
  unresolvedIssuesEvaluation: [
    'unresolvedIssuesEvaluation',
    'unresolved_issues_evaluation',
    'unresolvedIssues',
    'unresolved_issues',
    'openIssuesEvaluation',
    'open_issues_evaluation',
    'openIssues',
    'open_issues',
    'pendingIssues',
    'pending_issues',
    '미해결이슈평가'
  ],
  nextMeetingSuggestions: [
    'nextMeetingSuggestions',
    'next_meeting_suggestions',
    'nextMeeting',
    'next_meeting',
    'recommendations',
    'suggestions',
    'nextSteps',
    'next_steps',
    '다음회의제안'
  ]
}

const facilitatorMetadataKeys = [
  'meetingId',
  'meeting_id',
  'meetingRoomName',
  'meeting_room_name',
  'createdAt',
  'created_at',
  'updatedAt',
  'updated_at',
  'mdUrl',
  'md_url',
  'pdfUrl',
  'pdf_url',
  'data',
  'result',
  'report',
  'feedback',
  'analysis',
  'details',
  'sections'
]

const toSectionEntries = (sections) => {
  const parsed = parseJsonValue(sections)
  if (Array.isArray(parsed)) {
    return parsed.map((section, index) => ({
      key: section?.key || section?.id || `section-${index}`,
      label: section?.label || section?.title || section?.name || `추가 분석 ${index + 1}`,
      value: normalizeDynamicValue(
        firstValue(section?.value, section?.items, section?.content, section?.data, section)
      )
    }))
  }
  if (parsed && typeof parsed === 'object') {
    return Object.entries(parsed).map(([key, value]) => ({
      key,
      label: key,
      value: normalizeDynamicValue(value)
    }))
  }
  return []
}

export const normalizeFacilitatorReport = (response) => {
  const parsedResponse = normalizeDynamicValue(response) || {}
  const scopes = collectObjectScopes(parsedResponse)
  const normalized = Object.fromEntries(
    Object.entries(facilitatorFields).map(([field, aliases]) => [
      field,
      normalizeDynamicValue(findAliasedValue(scopes, aliases))
    ])
  )

  const consumedKeys = new Set(
    [...Object.values(facilitatorFields).flat(), ...facilitatorMetadataKeys].map(normalizeKey)
  )
  const explicitSections = toSectionEntries(findAliasedValue(scopes, ['sections', 'additionalSections']))
  const rootExtras = Object.entries(parsedResponse)
    .filter(([key, value]) => !consumedKeys.has(normalizeKey(key)) && value != null && value !== '')
    .map(([key, value]) => ({ key, label: key, value: normalizeDynamicValue(value) }))

  return {
    ...normalized,
    additionalFeedbackSections: [...explicitSections, ...rootExtras]
  }
}

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
    const facilitator = normalizeFacilitatorReport(response)
    return {
      ...listRow,
      id: firstValue(response?.meetingId, response?.meeting_id, listRow.id),
      kind: 'facilitator',
      title: facilitator.title || listRow.title,
      meetingType: facilitator.meetingType || listRow.meetingType || '',
      overallReview: facilitator.overallReview || '',
      participationComment: facilitator.participationComment || '',
      participationStats: facilitator.participationStats ?? [],
      qualityEvaluation: facilitator.qualityEvaluation ?? [],
      strengths: facilitator.strengths ?? [],
      improvements: facilitator.improvements ?? [],
      decisionProcessChecks: facilitator.decisionProcessChecks ?? [],
      unresolvedIssuesEvaluation: facilitator.unresolvedIssuesEvaluation ?? [],
      nextMeetingSuggestions: facilitator.nextMeetingSuggestions ?? [],
      additionalFeedbackSections: facilitator.additionalFeedbackSections,
      createdAt: firstValue(response?.createdAt, response?.created_at),
      updatedAt: formatReportDate(
        firstValue(response?.createdAt, response?.created_at, listRow.updatedAt)
      )
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
