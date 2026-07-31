<template>
  <TeamLayout active-section="documents" :retry="reloadBoard">
    <header class="page-heading document-editor-heading">
      <div>
        <span class="eyebrow">SHARED DOCUMENT</span>
        <h1>{{ documentTitle }}</h1>
        <p>문서 정보를 확인하고 팀 권한에 따라 내용을 편집하세요.</p>
      </div>
      <div class="document-heading-actions">
        <span class="permission-badge" :class="{ readonly: !canEdit }">
          {{ permissionLabel }}
        </span>
        <button class="outline-btn" type="button" @click="goBack">
          ← 문서 목록
        </button>
      </div>
    </header>

    <AsyncState
      v-if="
        documentState.loadingDocument ||
        documentState.error ||
        !currentDocument
      "
      :loading="documentState.loadingDocument"
      :error="documentState.error"
      :empty="
        !documentState.loadingDocument &&
        !documentState.error &&
        !currentDocument
      "
      :retry="loadDocument"
    />

    <template v-else>
      <section class="document-meta-grid">
        <article>
          <span>문서 ID</span>
          <b class="document-id">{{ currentDocument.documentId }}</b>
        </article>
        <article>
          <span>최종 버전</span>
          <b>v{{ currentDocument.finalVersion }}</b>
        </article>
        <article>
          <span>생성일</span>
          <b>{{ formatDate(currentDocument.createdAt) }}</b>
        </article>
        <article>
          <span>최근 업데이트</span>
          <b>{{ formatDate(currentDocument.updatedAt) }}</b>
        </article>
      </section>

      <section class="editor-card">
        <div class="editor-card-header">
          <div>
            <span class="editor-card-eyebrow">TIPTAP EDITOR</span>
            <EditorContent
              v-if="titleEditor"
              class="document-title-editor"
              :class="{ readonly: !canEdit || !collaborationSynced }"
              :editor="titleEditor"
            />
          </div>
          <div class="editor-presence">
            <div
              class="participant-list"
              role="list"
              :aria-label="`현재 참가자 ${participants.length}명`"
            >
              <span>참가자 {{ participants.length }}</span>
              <div v-if="participants.length" class="participant-chips">
                <div
                  v-for="participant in participants"
                  :key="participant.id"
                  class="participant-chip"
                  role="listitem"
                  :title="participant.name"
                >
                  <i
                    :style="{ '--participant-color': participant.color }"
                  >
                    {{ participant.avatarText }}
                  </i>
                  <b>{{ participant.name }}</b>
                  <small v-if="participant.isMe">나</small>
                </div>
              </div>
              <small v-else>연결 대기 중</small>
            </div>
            <div class="editor-state">
              <i :class="connectionIndicatorClass"></i>
              <span>{{ editorStateLabel }}</span>
            </div>
          </div>
        </div>

        <div v-if="canEdit" class="editor-toolbar" role="toolbar" aria-label="문서 서식">
          <div class="toolbar-group">
            <button
              type="button"
              title="실행 취소"
              aria-label="실행 취소"
              :disabled="!canUndo"
              @click="runCommand(chain => chain.undo())"
            >
              ↶
            </button>
            <button
              type="button"
              title="다시 실행"
              aria-label="다시 실행"
              :disabled="!canRedo"
              @click="runCommand(chain => chain.redo())"
            >
              ↷
            </button>
          </div>

          <div class="toolbar-group">
            <button
              type="button"
              :class="{ active: isActive('paragraph') }"
              @click="runCommand(chain => chain.setParagraph())"
            >
              본문
            </button>
            <button
              type="button"
              :class="{ active: isActive('heading', { level: 1 }) }"
              @click="runCommand(chain => chain.toggleHeading({ level: 1 }))"
            >
              제목 1
            </button>
            <button
              type="button"
              :class="{ active: isActive('heading', { level: 2 }) }"
              @click="runCommand(chain => chain.toggleHeading({ level: 2 }))"
            >
              제목 2
            </button>
          </div>

          <div class="toolbar-group">
            <button
              class="format-bold"
              type="button"
              aria-label="굵게"
              title="굵게"
              :class="{ active: isActive('bold') }"
              @click="runCommand(chain => chain.toggleBold())"
            >
              B
            </button>
            <button
              class="format-italic"
              type="button"
              aria-label="기울임"
              title="기울임"
              :class="{ active: isActive('italic') }"
              @click="runCommand(chain => chain.toggleItalic())"
            >
              I
            </button>
            <button
              class="format-strike"
              type="button"
              aria-label="취소선"
              title="취소선"
              :class="{ active: isActive('strike') }"
              @click="runCommand(chain => chain.toggleStrike())"
            >
              S
            </button>
            <button
              type="button"
              aria-label="인라인 코드"
              title="인라인 코드"
              :class="{ active: isActive('code') }"
              @click="runCommand(chain => chain.toggleCode())"
            >
              &lt;/&gt;
            </button>
          </div>

          <div class="toolbar-group">
            <button
              type="button"
              :class="{ active: isActive('bulletList') }"
              @click="runCommand(chain => chain.toggleBulletList())"
            >
              • 목록
            </button>
            <button
              type="button"
              :class="{ active: isActive('orderedList') }"
              @click="runCommand(chain => chain.toggleOrderedList())"
            >
              1. 목록
            </button>
            <button
              type="button"
              :class="{ active: isActive('blockquote') }"
              @click="runCommand(chain => chain.toggleBlockquote())"
            >
              인용
            </button>
          </div>
        </div>

        <div v-else class="readonly-notice">
          <strong>읽기 전용 문서</strong>
          <span>서버에서 READ 권한이 확인되어 문서 내용을 수정할 수 없습니다.</span>
        </div>

        <EditorContent v-if="editor" class="editor-content" :editor="editor" />

        <footer class="editor-footer">
          <span>State epoch {{ currentDocument.stateEpoch }}</span>
        </footer>
      </section>
    </template>
  </TeamLayout>
</template>

<script setup>
import { Editor, EditorContent } from '@tiptap/vue-3'
import Collaboration from '@tiptap/extension-collaboration'
import CollaborationCaret from '@tiptap/extension-collaboration-caret'
import Placeholder from '@tiptap/extension-placeholder'
import StarterKit from '@tiptap/starter-kit'
import { HocuspocusProvider } from '@hocuspocus/provider'
import * as Y from 'yjs'
import {
  computed,
  onBeforeUnmount,
  onMounted,
  ref,
  shallowRef,
  watch
} from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AsyncState from '../../../shared/components/AsyncState.vue'
import TeamLayout from '../../board/components/TeamLayout.vue'
import { useBoardPage } from '../../board/composables/useBoardPage'
import { authStore } from '../../auth/stores/authStore'
import { userStore } from '../../user/stores/userStore'
import { documentStore } from '../stores/documentStore'

const route = useRoute()
const router = useRouter()
const documentState = documentStore.state
const editor = shallowRef(null)
const titleEditor = shallowRef(null)
const editorRevision = ref(0)
const collaborationPermission = ref('')
const collaborationStatus = ref('idle')
const collaborationSynced = ref(false)
const collaborationError = ref('')
const collaborativeTitle = ref('')
const titleReady = ref(false)
const participants = ref([])
let collaborationProvider = null
let collaborationDocument = null
let connectionGeneration = 0
let currentCollaboration = null
let editorStateFrame = null
let titleStateFrame = null

function getDefaultCollaborationUrl() {
  if (typeof window === 'undefined') {
    return 'ws://127.0.0.1:3000/collaboration'
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/collaboration`
}

const collaborationUrl =
  import.meta.env.VITE_YJS_WEBSOCKET_URL ||
  getDefaultCollaborationUrl()

const documentId = computed(() => String(route.params.documentId || ''))
const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team']
})

const currentDocument = computed(() =>
  documentState.currentDocument?.documentId === documentId.value
    ? documentState.currentDocument
    : null
)
const titleInputValue = computed(() =>
  titleReady.value
    ? collaborativeTitle.value
    : currentDocument.value?.title || ''
)
const documentTitle = computed(
  () => titleInputValue.value.trim() || '새 문서'
)
const authority = computed(() =>
  String(boardState.team.role || '').toUpperCase()
)
const currentAwarenessUser = computed(() => {
  const profile = userStore.state.profile || {}
  const userId =
    profile.userId ?? authStore.state.userId ?? authStore.state.user?.userId
  const name = String(
    profile.nickname ||
    profile.email ||
    (userId ? `사용자 ${userId}` : '사용자')
  ).slice(0, 30)
  const color = /^#[0-9a-f]{6}$/i.test(profile.userColor || '')
    ? profile.userColor
    : '#5f6fe5'

  return {
    id: userId === null || userId === undefined ? '' : String(userId),
    name,
    color,
    avatarText: String(profile.avatarText || name || '?').slice(0, 1)
  }
})
const canEdit = computed(() => collaborationPermission.value === 'WRITE')
const permissionLabel = computed(() => {
  const role =
    authority.value === 'OWNER'
      ? 'Owner'
      : authority.value === 'MEMBER'
        ? 'Member'
        : authority.value === 'GUEST'
          ? 'Guest'
          : '팀 구성원'

  if (collaborationPermission.value === 'WRITE') {
    return `${role} · 편집 가능`
  }
  if (collaborationPermission.value === 'READ') {
    return `${role} · 읽기 전용`
  }
  return `${role} · 권한 확인 중`
})
const editorStateLabel = computed(() => {
  if (collaborationError.value) return collaborationError.value
  if (collaborationStatus.value === 'connecting') {
    return '실시간 서버 연결 중'
  }
  if (collaborationStatus.value === 'disconnected') {
    return '연결 끊김 · 자동 재연결 중'
  }
  if (
    collaborationStatus.value === 'connected' &&
    !collaborationSynced.value
  ) {
    return '문서 동기화 중'
  }
  if (collaborationSynced.value && !canEdit.value) {
    return '실시간 동기화됨 · 읽기 전용'
  }
  if (collaborationSynced.value) return '실시간 동기화됨'
  return '협업 연결 준비 중'
})
const connectionIndicatorClass = computed(() => ({
  connected: collaborationSynced.value,
  connecting: collaborationStatus.value === 'connecting',
  disconnected: collaborationStatus.value === 'disconnected',
  error: Boolean(collaborationError.value)
}))
const canUndo = computed(() => {
  editorRevision.value
  return Boolean(
    canEdit.value &&
    collaborationSynced.value &&
    editor.value?.can().undo()
  )
})
const canRedo = computed(() => {
  editorRevision.value
  return Boolean(
    canEdit.value &&
    collaborationSynced.value &&
    editor.value?.can().redo()
  )
})

function touchEditorState() {
  if (editorStateFrame !== null) return

  editorStateFrame = window.requestAnimationFrame(() => {
    editorStateFrame = null
    editorRevision.value += 1
  })
}

function updateParticipants(states = []) {
  const uniqueParticipants = new Map()

  states.forEach(state => {
    const user = state?.user
    if (!user?.name) return

    const userId = user.id === undefined || user.id === null
      ? ''
      : String(user.id)
    const id = userId
      ? `user:${userId}`
      : `client:${state.clientId}`
    const name = String(user.name).slice(0, 30)
    const color = /^#[0-9a-f]{6}$/i.test(user.color || '')
      ? user.color
      : '#5f6fe5'

    if (!uniqueParticipants.has(id)) {
      uniqueParticipants.set(id, {
        id,
        name,
        color,
        avatarText: String(user.avatarText || name || '?').slice(0, 1),
        isMe:
          (
            Boolean(userId) &&
            userId === currentAwarenessUser.value.id
          ) ||
          state.clientId === collaborationDocument?.clientID
      })
    }
  })

  participants.value = [...uniqueParticipants.values()].sort((a, b) => {
    if (a.isMe !== b.isMe) return a.isMe ? -1 : 1
    return a.name.localeCompare(b.name, 'ko')
  })
}

function readTitleEditorText() {
  return titleEditor.value
    ?.getText({ blockSeparator: ' ' })
    .replace(/\s+/g, ' ')
    .slice(0, 1000) || ''
}

function syncTitleFromEditor() {
  if (titleStateFrame !== null) return

  titleStateFrame = window.requestAnimationFrame(() => {
    titleStateFrame = null
    if (!titleEditor.value || !currentDocument.value) return

    collaborativeTitle.value = readTitleEditorText()
    titleReady.value = true
    documentStore.syncDocumentTitle(
      currentDocument.value.documentId,
      collaborativeTitle.value
    )
  })
}

function refreshSyncedTitle() {
  if (!titleEditor.value) return

  titleEditor.value.view.dispatch(
    titleEditor.value.state.tr.setMeta('collaboration-sync-render', true)
  )
  syncTitleFromEditor()
}

function normalizeDocumentTitle() {
  if (
    !canEdit.value ||
    !collaborationSynced.value ||
    !titleEditor.value ||
    readTitleEditorText().trim()
  ) return

  titleEditor.value.commands.insertContent('새 문서')
}

function destroyEditor() {
  connectionGeneration += 1

  if (editorStateFrame !== null) {
    window.cancelAnimationFrame(editorStateFrame)
    editorStateFrame = null
  }
  if (titleStateFrame !== null) {
    window.cancelAnimationFrame(titleStateFrame)
    titleStateFrame = null
  }

  editor.value?.destroy()
  editor.value = null
  titleEditor.value?.destroy()
  titleEditor.value = null

  collaborationProvider?.destroy()
  collaborationProvider = null

  collaborationDocument?.destroy()
  collaborationDocument = null

  currentCollaboration = null
  collaborationPermission.value = ''
  collaborationStatus.value = 'idle'
  collaborationSynced.value = false
  collaborationError.value = ''
  collaborativeTitle.value = ''
  titleReady.value = false
  participants.value = []
}

function isTokenUsable(collaboration) {
  if (!collaboration?.token) return false

  const expiresAt = new Date(collaboration.expiresAt).getTime()
  return Number.isFinite(expiresAt) && expiresAt - Date.now() > 30_000
}

async function resolveCollaborationToken(documentId, generation) {
  if (!isTokenUsable(currentCollaboration)) {
    const collaboration =
      await documentStore.issueCollaborationToken(documentId)

    if (generation !== connectionGeneration) {
      throw new Error('종료된 문서 연결입니다.')
    }

    currentCollaboration = collaboration
    collaborationPermission.value = collaboration.permission
    editor.value?.setEditable(
      collaboration.permission === 'WRITE' &&
      collaborationSynced.value
    )
    titleEditor.value?.setEditable(
      collaboration.permission === 'WRITE' &&
      collaborationSynced.value
    )
    touchEditorState()
  }

  return currentCollaboration.token
}

async function createCollaborativeEditor(document, generation) {
  const collaboration =
    await documentStore.issueCollaborationToken(document.documentId)

  if (generation !== connectionGeneration) return

  currentCollaboration = collaboration
  collaborationPermission.value = currentCollaboration.permission
  collaborationDocument = new Y.Doc()
  collaborationStatus.value = 'connecting'

  collaborationProvider = new HocuspocusProvider({
    url: collaborationUrl,
    autoConnect: false,
    name: `document:${document.documentId}:epoch:${document.stateEpoch}`,
    document: collaborationDocument,
    flushDelay: 30,
    token: () =>
      resolveCollaborationToken(document.documentId, generation),
    onStatus: ({ status }) => {
      if (generation !== connectionGeneration) return
      collaborationStatus.value = status
      if (status !== 'connected') collaborationSynced.value = false
    },
    onSynced: ({ state }) => {
      if (generation !== connectionGeneration) return
      collaborationSynced.value = state !== false
      collaborationError.value = ''
      refreshSyncedTitle()
    },
    onAwarenessChange: ({ states }) => {
      if (generation !== connectionGeneration) return
      updateParticipants(states)
    },
    onAuthenticationFailed: ({ reason }) => {
      if (generation !== connectionGeneration) return
      collaborationStatus.value = 'error'
      collaborationSynced.value = false
      collaborationError.value =
        reason || '실시간 편집 인증에 실패했습니다.'
    },
    onDisconnect: () => {
      if (generation !== connectionGeneration) return
      collaborationStatus.value = 'disconnected'
      collaborationSynced.value = false
      participants.value = []
    }
  })

  titleEditor.value = new Editor({
    extensions: [
      StarterKit.configure({
        undoRedo: false,
        heading: false,
        blockquote: false,
        bulletList: false,
        orderedList: false,
        listItem: false,
        codeBlock: false,
        horizontalRule: false,
        hardBreak: false,
        bold: false,
        italic: false,
        strike: false,
        code: false
      }),
      Collaboration.configure({
        document: collaborationDocument,
        field: 'title-content'
      }),
      CollaborationCaret.configure({
        provider: collaborationProvider,
        user: currentAwarenessUser.value
      }),
      Placeholder.configure({
        placeholder: '새 문서'
      })
    ],
    editable: false,
    editorProps: {
      attributes: {
        class: 'document-title-prose',
        role: 'textbox',
        'aria-label': '문서 제목',
        'aria-multiline': 'false'
      },
      handleKeyDown: (_view, event) => event.key === 'Enter',
      handleTextInput: (view, from, to, text) => {
        const selectedLength = view.state.doc.textBetween(from, to).length
        const availableLength =
          1000 - (view.state.doc.textContent.length - selectedLength)

        if (text.length <= availableLength) return false
        if (availableLength > 0) {
          view.dispatch(
            view.state.tr.insertText(text.slice(0, availableLength), from, to)
          )
        }
        return true
      },
      handlePaste: (view, event) => {
        const clipboardText = event.clipboardData
          ?.getData('text/plain')
          .replace(/\s+/g, ' ')

        if (!clipboardText) return true

        event.preventDefault()
        const { from, to } = view.state.selection
        const selectedLength = view.state.doc.textBetween(from, to).length
        const availableLength =
          1000 - (view.state.doc.textContent.length - selectedLength)
        const text = clipboardText.slice(0, Math.max(availableLength, 0))

        if (!text) return true

        view.dispatch(
          view.state.tr.insertText(text, from, to)
        )
        return true
      }
    },
    onCreate: syncTitleFromEditor,
    onTransaction: syncTitleFromEditor,
    onBlur: normalizeDocumentTitle
  })

  editor.value = new Editor({
    extensions: [
      StarterKit.configure({
        undoRedo: false,
        heading: {
          levels: [1, 2, 3]
        }
      }),
      Collaboration.configure({
        document: collaborationDocument,
        field: 'default'
      }),
      CollaborationCaret.configure({
        provider: collaborationProvider,
        user: currentAwarenessUser.value
      }),
      Placeholder.configure({
        placeholder: '문서 내용을 입력하세요.'
      })
    ],
    editable: false,
    editorProps: {
      attributes: {
        class: 'document-prose',
        'aria-label': '문서 내용 편집 영역'
      }
    },
    onCreate: touchEditorState,
    onTransaction: touchEditorState
  })

  await collaborationProvider.connect()
}

function isActive(name, attributes) {
  editorRevision.value
  return editor.value?.isActive(name, attributes) || false
}

function runCommand(command) {
  if (!canEdit.value || !collaborationSynced.value || !editor.value) return
  command(editor.value.chain().focus()).run()
  touchEditorState()
}

function formatDate(value) {
  if (!value) return '-'

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'

  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

async function loadDocument() {
  destroyEditor()

  if (!documentId.value) return
  const generation = connectionGeneration
  const requestedDocumentId = documentId.value

  try {
    const document = await documentStore.loadDocument(requestedDocumentId)

    if (
      generation !== connectionGeneration ||
      requestedDocumentId !== documentId.value
    ) {
      return
    }

    await createCollaborativeEditor(document, generation)
  } catch {
    // 오류 문구와 재시도 동작은 AsyncState에서 처리한다.
  }
}

function goBack() {
  router.push({
    name: 'team-documents',
    params: { teamId: teamId.value }
  })
}

watch([canEdit, collaborationSynced], ([editable, synced]) => {
  editor.value?.setEditable(editable && synced)
  titleEditor.value?.setEditable(editable && synced)
  touchEditorState()
})
watch(currentAwarenessUser, user => {
  collaborationProvider?.setAwarenessField('user', user)
  titleEditor.value?.commands.updateUser(user)
  editor.value?.commands.updateUser(user)
})
watch(documentId, loadDocument)

onMounted(loadDocument)
onBeforeUnmount(() => {
  destroyEditor()
  documentStore.clearCurrentDocument()
})
</script>

<style scoped>
.document-editor-heading {
  align-items: flex-start;
  margin-bottom: 18px;
}

.document-heading-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.permission-badge {
  padding: 7px 11px;
  border-radius: 18px;
  background: #eaf8ef;
  color: #27824b;
  font-size: 10px;
  font-weight: 800;
}

.permission-badge.readonly {
  background: #eef1f7;
  color: #697287;
}

.document-meta-grid {
  display: grid;
  grid-template-columns: minmax(220px, 1.7fr) repeat(3, minmax(130px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.document-meta-grid article {
  display: grid;
  gap: 6px;
  min-width: 0;
  padding: 13px 15px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: #fff;
}

.document-meta-grid span {
  color: var(--muted);
  font-size: 9px;
}

.document-meta-grid b {
  overflow: hidden;
  font-size: 11px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-id {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}

.editor-card {
  overflow: hidden;
  border: 1px solid var(--line);
  border-radius: 13px;
  background: #fff;
  box-shadow: 0 14px 32px #2437610a;
}

.editor-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line);
}

.editor-card-eyebrow {
  color: var(--blue);
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.document-title-editor {
  display: block;
  width: min(520px, 48vw);
  margin: 4px 0 0;
}

.document-title-editor :deep(.document-title-prose) {
  padding: 0;
  border: 0;
  background: transparent;
  color: #273047;
  font-family: inherit;
  font-size: 16px;
  font-weight: 800;
  outline: none;
  overflow: hidden;
  white-space: nowrap;
}

.document-title-editor :deep(.document-title-prose p) {
  margin: 0;
}

.document-title-editor:not(.readonly):focus-within {
  box-shadow: 0 2px 0 var(--blue);
}

.document-title-editor.readonly {
  cursor: default;
}

.editor-presence {
  display: grid;
  justify-items: end;
  gap: 8px;
}

.participant-list,
.participant-chips,
.participant-chip {
  display: flex;
  align-items: center;
}

.participant-list {
  justify-content: flex-end;
  gap: 8px;
  color: #70798c;
  font-size: 9px;
}

.participant-list > span {
  flex: 0 0 auto;
  font-weight: 800;
}

.participant-list > small {
  color: #9aa2b1;
}

.participant-chips {
  overflow-x: auto;
  justify-content: flex-end;
  gap: 5px;
  max-width: min(48vw, 520px);
  padding: 2px;
}

.participant-chip {
  flex: 0 0 auto;
  gap: 5px;
  padding: 3px 7px 3px 4px;
  border: 1px solid #e3e6ee;
  border-radius: 16px;
  background: #fff;
}

.participant-chip > i {
  display: grid;
  width: 20px;
  height: 20px;
  place-items: center;
  border-radius: 50%;
  background: var(--participant-color);
  color: #fff;
  font-size: 8px;
  font-style: normal;
  font-weight: 800;
}

.participant-chip > b {
  max-width: 90px;
  overflow: hidden;
  color: #4d5669;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.participant-chip > small {
  padding: 1px 4px;
  border-radius: 8px;
  background: #eef1ff;
  color: var(--blue2);
  font-size: 7px;
  font-weight: 800;
}

.editor-state {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #6e7789;
  font-size: 9px;
}

.editor-state i {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #7c8ba8;
  box-shadow: 0 0 0 4px #eef1f7;
}

.editor-state i.connecting,
.editor-state i.disconnected {
  background: #e5962f;
  box-shadow: 0 0 0 4px #fff2df;
}

.editor-state i.connected {
  background: #35a765;
  box-shadow: 0 0 0 4px #e5f7ec;
}

.editor-state i.error {
  background: #d65050;
  box-shadow: 0 0 0 4px #fde8e8;
}

.editor-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
  padding: 9px 13px;
  border-bottom: 1px solid var(--line);
  background: #fafbfe;
}

.toolbar-group {
  display: flex;
  gap: 3px;
  padding-right: 7px;
  border-right: 1px solid #e1e5ed;
}

.toolbar-group:last-child {
  padding-right: 0;
  border-right: 0;
}

.editor-toolbar button {
  min-width: 30px;
  height: 29px;
  padding: 0 8px;
  border: 1px solid transparent;
  border-radius: 6px;
  background: transparent;
  color: #475166;
  font-size: 10px;
  font-weight: 700;
}

.editor-toolbar button:hover:not(:disabled) {
  border-color: #d7dcf7;
  background: #eef1ff;
  color: var(--blue2);
}

.editor-toolbar button.active {
  border-color: #cfd6ff;
  background: #e5e9ff;
  color: var(--blue2);
}

.editor-toolbar button:disabled {
  cursor: not-allowed;
  opacity: 0.35;
}

.format-bold {
  font-weight: 900 !important;
}

.format-italic {
  font-style: italic;
}

.format-strike {
  text-decoration: line-through;
}

.readonly-notice {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 16px;
  border-bottom: 1px solid #e3e6ed;
  background: #f7f8fb;
  color: #6c7588;
  font-size: 10px;
}

.readonly-notice strong {
  color: #454f63;
}

.editor-content {
  min-height: 440px;
  background: #fff;
}

.editor-footer {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  padding: 11px 17px;
  border-top: 1px solid var(--line);
  background: #fafbfc;
  color: #7a8496;
  font-size: 9px;
}

:deep(.document-prose) {
  min-height: 440px;
  padding: 34px clamp(24px, 7vw, 92px) 80px;
  color: #273047;
  font-size: 14px;
  line-height: 1.8;
  outline: none;
}

:deep(.document-prose > *:first-child) {
  margin-top: 0;
}

:deep(.document-prose h1) {
  margin: 1.5em 0 0.6em;
  font-size: 28px;
  line-height: 1.25;
}

:deep(.document-prose h2) {
  margin: 1.4em 0 0.55em;
  font-size: 22px;
  line-height: 1.3;
}

:deep(.document-prose h3) {
  margin: 1.3em 0 0.5em;
  font-size: 18px;
}

:deep(.document-prose p) {
  margin: 0.75em 0;
}

:deep(.document-prose ul),
:deep(.document-prose ol) {
  margin: 0.8em 0;
  padding-left: 1.6em;
}

:deep(.document-prose blockquote) {
  margin: 1.2em 0;
  padding: 8px 16px;
  border-left: 3px solid #7889ee;
  background: #f6f7ff;
  color: #586176;
}

:deep(.document-prose code) {
  padding: 2px 5px;
  border-radius: 4px;
  background: #eff1f6;
  color: #b33b55;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.9em;
}

:deep(.document-prose pre) {
  overflow-x: auto;
  padding: 14px 16px;
  border-radius: 8px;
  background: #252b3a;
  color: #f5f7ff;
}

:deep(.document-prose pre code) {
  padding: 0;
  background: transparent;
  color: inherit;
}

:deep(.document-prose p.is-editor-empty:first-child::before) {
  float: left;
  height: 0;
  color: #a6adbb;
  content: attr(data-placeholder);
  pointer-events: none;
}

:deep(.collaboration-carets__caret) {
  position: relative;
  margin-right: -1px;
  margin-left: -1px;
  border-right: 1px solid;
  border-left: 1px solid;
  pointer-events: none;
  word-break: normal;
}

:deep(.collaboration-carets__label) {
  position: absolute;
  top: -1.55em;
  left: -1px;
  z-index: 2;
  padding: 2px 6px;
  border-radius: 4px 4px 4px 0;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  line-height: 1.25;
  user-select: none;
  white-space: nowrap;
}

:deep(.ProseMirror-yjs-selection) {
  border-radius: 2px;
}

@media (max-width: 920px) {
  .document-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .document-editor-heading {
    gap: 12px;
  }

  .document-heading-actions {
    align-items: flex-end;
    flex-direction: column;
  }

  .document-meta-grid {
    grid-template-columns: 1fr;
  }

  .editor-card-header,
  .editor-footer {
    align-items: flex-start;
    flex-direction: column;
  }

  .editor-presence {
    width: 100%;
    justify-items: start;
  }

  .participant-list {
    max-width: 100%;
    justify-content: flex-start;
  }

  .participant-chips {
    max-width: min(70vw, 520px);
    justify-content: flex-start;
  }

  .document-title-editor {
    width: min(75vw, 520px);
  }

  :deep(.document-prose) {
    padding: 26px 20px 60px;
  }
}

/* 목록 화면 및 코밋툴 공통 80% 스케일과 동일한 편집기 UI */
.document-editor-heading {
  min-height: 54px;
  margin-bottom: 16px;
  font-family: 'Noto Sans KR', sans-serif;
}

.document-editor-heading h1 {
  margin: 4.8px 0 2.4px;
  font-size: 21.6px;
  line-height: 1.3;
  letter-spacing: -0.8px;
}

.document-editor-heading p {
  font-size: 11.2px;
  line-height: 1.55;
}

.document-heading-actions {
  gap: 8px;
}

.permission-badge,
.document-heading-actions .outline-btn {
  min-height: 32px;
  padding: 7.2px 11.2px;
  border-radius: 16px;
  font-size: 9.6px;
}

.document-heading-actions .outline-btn {
  border-radius: 6.4px;
}

.document-meta-grid {
  grid-template-columns:
    minmax(260px, 1.55fr) minmax(88px, 0.42fr)
    repeat(2, minmax(170px, 0.85fr));
  gap: 8px;
  margin-bottom: 10px;
}

.document-meta-grid article {
  min-height: 62px;
  gap: 5px;
  padding: 11px 12px;
  border-width: 0.8px;
  border-radius: 8px;
}

.document-meta-grid span {
  font-size: 11px;
  font-weight: 700;
  line-height: 1.3;
}

.document-meta-grid b {
  font-size: 11.2px;
  line-height: 1.35;
}

.editor-card {
  border-width: 0.8px;
  border-radius: 12px;
  font-family: 'Noto Sans KR', sans-serif;
  box-shadow: 0 8px 24px #2437610a;
}

.editor-card-header {
  min-height: 80px;
  gap: 12.8px;
  padding: 17.6px 18px;
  border-bottom-width: 0.8px;
}

.editor-card-eyebrow {
  font-size: 8px;
  letter-spacing: 1.2px;
}

.document-title-editor {
  width: min(520px, 46vw);
  margin-top: 5px;
}

.document-title-editor :deep(.document-title-prose) {
  font-size: 18.4px;
  line-height: 1.45;
  letter-spacing: -0.4px;
}

.editor-presence {
  gap: 6.4px;
}

.participant-list,
.editor-state {
  font-size: 8.8px;
}

.participant-chip {
  gap: 4px;
  padding: 2.4px 6.4px 2.4px 3.2px;
  border-width: 0.8px;
}

.participant-chip > i {
  width: 19.2px;
  height: 19.2px;
  font-size: 7.2px;
}

.participant-chip > b {
  font-size: 8.8px;
}

.editor-toolbar {
  gap: 5.6px;
  padding: 7.2px 10.4px;
  border-bottom-width: 0.8px;
}

.toolbar-group {
  gap: 2.4px;
  padding-right: 5.6px;
  border-right-width: 0.8px;
}

.editor-toolbar button {
  min-width: 28.8px;
  height: 27.2px;
  padding: 0 7.2px;
  border-width: 0.8px;
  border-radius: 4.8px;
  font-size: 9.6px;
}

.readonly-notice {
  padding: 8px 12.8px;
  border-bottom-width: 0.8px;
  font-size: 9.6px;
}

.editor-content,
:deep(.document-prose) {
  min-height: 384px;
}

:deep(.document-prose) {
  padding: 32px clamp(24px, 6vw, 76.8px) 68px;
  color: var(--text);
  font-family: 'Noto Sans KR', sans-serif;
  font-size: 14.4px;
  line-height: 1.8;
}

:deep(.document-prose h1) {
  margin: 1.4em 0 0.55em;
  font-size: 26.4px;
  line-height: 1.3;
  letter-spacing: -0.8px;
}

:deep(.document-prose h2) {
  margin: 1.35em 0 0.5em;
  font-size: 21.6px;
  line-height: 1.35;
  letter-spacing: -0.5px;
}

:deep(.document-prose h3) {
  margin: 1.3em 0 0.45em;
  font-size: 17.6px;
  line-height: 1.4;
}

:deep(.document-prose p) {
  margin: 0.65em 0;
}

:deep(.document-prose ul),
:deep(.document-prose ol) {
  margin: 0.7em 0;
  padding-left: 1.5em;
}

.editor-footer {
  padding: 8.8px 13.6px;
  border-top-width: 0.8px;
  font-size: 8.8px;
}

@media (max-width: 1100px) {
  .document-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .document-title-input {
    width: min(420px, 43vw);
  }
}
</style>
