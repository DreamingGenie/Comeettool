import { cloneMockValue, mockResponse } from '../../../shared/api'
import { documentMockDatabase } from './documentMockDatabase'

function createDocumentId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()

  const suffix = String(Date.now()).padStart(12, '0').slice(-12)
  return `00000000-0000-4000-8000-${suffix}`
}

function findActiveDocument(documentId) {
  return documentMockDatabase.documents.find(
    document =>
      document.documentId === documentId &&
      !document.deleted
  )
}

function createMockError(message, code, status) {
  const error = new Error(message)
  error.code = code
  error.status = status
  return error
}

function toSummary(document) {
  const {
    documentId,
    teamId,
    title,
    finalVersion,
    updatedAt
  } = document

  return { documentId, teamId, title, finalVersion, updatedAt }
}

function toDetail(document) {
  const {
    documentId,
    teamId,
    title,
    finalVersion,
    stateEpoch,
    createdAt,
    updatedAt
  } = document

  return {
    documentId,
    teamId,
    title,
    finalVersion,
    stateEpoch,
    createdAt,
    updatedAt
  }
}

export const documentMockApi = {
  getDocumentList: teamId => {
    const documents = documentMockDatabase.documents
      .filter(
        document =>
          String(document.teamId) === String(teamId) &&
          !document.deleted
      )
      .sort((a, b) => new Date(b.updatedAt) - new Date(a.updatedAt))
      .map(toSummary)

    return mockResponse(documents)
  },
  getDocument: documentId => {
    const document = findActiveDocument(documentId)

    if (!document) {
      return Promise.reject(
        createMockError(
          '문서를 찾을 수 없습니다.',
          'DOCUMENT_NOT_FOUND',
          404
        )
      )
    }

    return mockResponse(toDetail(document))
  },
  createDocument: teamId => {
    const createdAt = new Date().toISOString()
    const document = {
      documentId: createDocumentId(),
      teamId,
      title: '새 문서',
      finalVersion: 0,
      stateEpoch: 1,
      createdAt,
      updatedAt: createdAt,
      deleted: false
    }

    documentMockDatabase.documents.unshift(document)
    return mockResponse(toDetail(document))
  },
  issueCollaborationToken: documentId => {
    if (!findActiveDocument(documentId)) {
      return Promise.reject(
        createMockError(
          '문서를 찾을 수 없습니다.',
          'DOCUMENT_NOT_FOUND',
          404
        )
      )
    }

    return mockResponse({
      token: `mock-collaboration-token-${documentId}`,
      expiresAt: new Date(Date.now() + 1000 * 60 * 5).toISOString(),
      permission: 'WRITE'
    })
  },
  deleteDocument: documentId => {
    const document = findActiveDocument(documentId)

    if (!document) {
      return Promise.reject(
        createMockError(
          '문서를 찾을 수 없습니다.',
          'DOCUMENT_NOT_FOUND',
          404
        )
      )
    }

    document.deleted = true
    return Promise.resolve(cloneMockValue(null))
  }
}