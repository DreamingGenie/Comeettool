import { reactive } from 'vue'
import { dataSource } from '../../../shared/api/dataSource'
import {
  mapCollaborationSession,
  mapDocumentDetail,
  mapDocumentSummary
} from '../model/documentMapper'

const state = reactive({
  documents: [],
  currentDocument: null,
  collaboration: null,
  currentTeamId: '',
  loadingList: false,
  loadingDocument: false,
  creating: false,
  issuingToken: false,
  deletingDocumentId: '',
  error: ''
})

function upsertDocument(document) {
  const summary = mapDocumentSummary(document)
  const index = state.documents.findIndex(
    item => item.documentId === summary.documentId
  )

  if (index >= 0) {
    state.documents[index] = summary
  } else {
    state.documents.unshift(summary)
  }
}

function setError(error, fallbackMessage) {
  state.error = error?.message || fallbackMessage
}

export const documentStore = {
  state,
  async loadDocumentList(teamId) {
    state.loadingList = true
    state.error = ''

    try {
      const documents = await dataSource.document.getDocumentList(teamId)
      state.documents = Array.isArray(documents)
        ? documents.map(mapDocumentSummary)
        : []
      state.currentTeamId = String(teamId)
      return state.documents
    } catch (error) {
      setError(error, '문서 목록을 불러오지 못했습니다.')
      throw error
    } finally {
      state.loadingList = false
    }
  },
  async loadDocument(documentId) {
    state.loadingDocument = true
    state.error = ''

    try {
      const response = await dataSource.document.getDocument(documentId)
      const document = mapDocumentDetail(response)
      state.currentDocument = document
      upsertDocument(document)
      return document
    } catch (error) {
      state.currentDocument = null
      setError(error, '문서를 불러오지 못했습니다.')
      throw error
    } finally {
      state.loadingDocument = false
    }
  },
  async createDocument(teamId) {
    state.creating = true
    state.error = ''

    try {
      const response = await dataSource.document.createDocument(teamId)
      const document = mapDocumentDetail(response)
      state.currentDocument = document
      state.currentTeamId = String(teamId)
      upsertDocument(document)
      return document
    } catch (error) {
      setError(error, '문서를 생성하지 못했습니다.')
      throw error
    } finally {
      state.creating = false
    }
  },
  async issueCollaborationToken(documentId) {
    state.issuingToken = true
    state.error = ''

    try {
      const response =
        await dataSource.document.issueCollaborationToken(documentId)
      const collaboration = mapCollaborationSession(response)
      state.collaboration = collaboration
      return collaboration
    } catch (error) {
      state.collaboration = null
      setError(error, '실시간 편집 권한을 확인하지 못했습니다.')
      throw error
    } finally {
      state.issuingToken = false
    }
  },
  async deleteDocument(documentId) {
    state.deletingDocumentId = documentId
    state.error = ''

    try {
      await dataSource.document.deleteDocument(documentId)
      state.documents = state.documents.filter(
        document => document.documentId !== documentId
      )

      if (state.currentDocument?.documentId === documentId) {
        state.currentDocument = null
        state.collaboration = null
      }
    } catch (error) {
      setError(error, '문서를 삭제하지 못했습니다.')
      throw error
    } finally {
      state.deletingDocumentId = ''
    }
  },
  syncDocumentTitle(documentId, title) {
    if (state.currentDocument?.documentId === documentId) {
      state.currentDocument.title = title
    }

    const summary = state.documents.find(
      document => document.documentId === documentId
    )
    if (summary) summary.title = title
  },
  clearCurrentDocument() {
    state.currentDocument = null
    state.collaboration = null
  },
  clearError() {
    state.error = ''
  },
  reset() {
    state.documents = []
    state.currentDocument = null
    state.collaboration = null
    state.currentTeamId = ''
    state.loadingList = false
    state.loadingDocument = false
    state.creating = false
    state.issuingToken = false
    state.deletingDocumentId = ''
    state.error = ''
  }
}
