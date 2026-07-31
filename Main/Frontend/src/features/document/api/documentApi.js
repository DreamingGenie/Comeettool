import { request } from '../../../shared/api'

function requirePositiveTeamId(teamId) {
  const parsedTeamId = Number(teamId)

  if (!Number.isSafeInteger(parsedTeamId) || parsedTeamId <= 0) {
    throw new Error('올바른 팀 ID가 필요합니다.')
  }

  return parsedTeamId
}

export const documentApi = {
  getDocumentList: teamId => {
    const parsedTeamId = requirePositiveTeamId(teamId)
    return request(
      `/api/v1/document/list?teamId=${encodeURIComponent(parsedTeamId)}`
    )
  },
  getDocument: documentId =>
    request(`/api/v1/document/${encodeURIComponent(documentId)}`),
  createDocument: teamId =>
    request('/api/v1/document', {
      method: 'POST',
      body: JSON.stringify({ teamId: requirePositiveTeamId(teamId) })
    }),
  issueCollaborationToken: documentId =>
    request(
      `/api/v1/document/${encodeURIComponent(documentId)}/collaboration-token`,
      { method: 'POST' }
    ),
  deleteDocument: documentId =>
    request(`/api/v1/document/${encodeURIComponent(documentId)}`, {
      method: 'DELETE'
    })
}
