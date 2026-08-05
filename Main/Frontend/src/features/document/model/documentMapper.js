function requireObject(value, label) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new Error(`${label} 응답 형식이 올바르지 않습니다.`)
  }

  return value
}

function requireString(value, label) {
  if (typeof value !== 'string' || !value.trim()) {
    throw new Error(`${label} 값이 누락되었습니다.`)
  }

  return value
}

function toNumber(value, fallback = 0) {
  const number = Number(value)
  return Number.isFinite(number) ? number : fallback
}

export function mapDocumentSummary(response) {
  const document = requireObject(response, '문서')

  return {
    documentId: requireString(document.documentId, '문서 ID'),
    teamId: document.teamId,
    title:
      typeof document.title === 'string' && document.title.trim()
        ? document.title
        : '새 문서',
    finalVersion: toNumber(document.finalVersion),
    updatedAt:
      typeof document.updatedAt === 'string' ? document.updatedAt : ''
  }
}

export function mapDocumentDetail(response) {
  const document = requireObject(response, '문서 상세')

  return {
    ...mapDocumentSummary(document),
    stateEpoch: toNumber(document.stateEpoch, 1),
    createdAt:
      typeof document.createdAt === 'string' ? document.createdAt : ''
  }
}

export function mapCollaborationSession(response) {
  const session = requireObject(response, '문서 협업 토큰')
  const permission = String(session.permission || 'READ').toUpperCase()

  return {
    token: requireString(session.token, '협업 토큰'),
    expiresAt: requireString(session.expiresAt, '협업 토큰 만료 시각'),
    permission: permission === 'WRITE' ? 'WRITE' : 'READ'
  }
}
