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
            <h2>{{ currentDocument.title }}</h2>
          </div>
          <div class="editor-state">
            <i :class="{ dirty: localDirty }"></i>
            <span>{{ editorStateLabel }}</span>
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
          <span>Guest 권한은 문서 내용을 수정할 수 없습니다.</span>
        </div>

        <EditorContent v-if="editor" class="editor-content" :editor="editor" />

        <footer class="editor-footer">
          <span>State epoch {{ currentDocument.stateEpoch }}</span>
          <span>
            실시간 저장과 다른 사용자와의 동기화는 4단계에서 연결됩니다.
          </span>
        </footer>
      </section>
    </template>
  </TeamLayout>
</template>

<script setup>
import { Editor, EditorContent } from '@tiptap/vue-3'
import Placeholder from '@tiptap/extension-placeholder'
import StarterKit from '@tiptap/starter-kit'
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
import { documentStore } from '../stores/documentStore'

const route = useRoute()
const router = useRouter()
const documentState = documentStore.state
const editor = shallowRef(null)
const editorRevision = ref(0)
const localDirty = ref(false)
let editorBaseline = ''

const documentId = computed(() => String(route.params.documentId || ''))
const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team']
})

const currentDocument = computed(() =>
  documentState.currentDocument?.documentId === documentId.value
    ? documentState.currentDocument
    : null
)
const documentTitle = computed(
  () => currentDocument.value?.title || '공유 문서'
)
const authority = computed(() =>
  String(boardState.team.role || '').toUpperCase()
)
const canEdit = computed(
  () => authority.value === 'OWNER' || authority.value === 'MEMBER'
)
const permissionLabel = computed(() => {
  if (authority.value === 'OWNER') return 'Owner · 편집 가능'
  if (authority.value === 'MEMBER') return 'Member · 편집 가능'
  if (authority.value === 'GUEST') return 'Guest · 읽기 전용'
  return '권한 확인 중'
})
const editorStateLabel = computed(() => {
  if (!canEdit.value) return '읽기 전용'
  if (localDirty.value) return '로컬 변경사항 · 아직 저장되지 않음'
  return '편집기 준비됨 · 실시간 저장 대기'
})
const canUndo = computed(() => {
  editorRevision.value
  return Boolean(
    canEdit.value &&
    editor.value?.can().chain().focus().undo().run()
  )
})
const canRedo = computed(() => {
  editorRevision.value
  return Boolean(
    canEdit.value &&
    editor.value?.can().chain().focus().redo().run()
  )
})

function touchEditorState() {
  editorRevision.value += 1
}

function destroyEditor() {
  editor.value?.destroy()
  editor.value = null
  editorBaseline = ''
}

function createEditor() {
  destroyEditor()
  localDirty.value = false

  editor.value = new Editor({
    extensions: [
      StarterKit.configure({
        heading: {
          levels: [1, 2, 3]
        }
      }),
      Placeholder.configure({
        placeholder: '문서 내용을 입력하세요.'
      })
    ],
    content: {
      type: 'doc',
      content: [{ type: 'paragraph' }]
    },
    editable: canEdit.value,
    editorProps: {
      attributes: {
        class: 'document-prose',
        'aria-label': '문서 내용 편집 영역'
      }
    },
    onCreate: ({ editor: instance }) => {
      editorBaseline = JSON.stringify(instance.getJSON())
      localDirty.value = false
      touchEditorState()
    },
    onSelectionUpdate: touchEditorState,
    onTransaction: touchEditorState,
    onUpdate: ({ editor: instance }) => {
      const currentContent = JSON.stringify(instance.getJSON())
      if (!editorBaseline) editorBaseline = currentContent
      localDirty.value = currentContent !== editorBaseline
      touchEditorState()
    }
  })
}

function isActive(name, attributes) {
  editorRevision.value
  return editor.value?.isActive(name, attributes) || false
}

function runCommand(command) {
  if (!canEdit.value || !editor.value) return
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
  localDirty.value = false

  if (!documentId.value) return

  try {
    await documentStore.loadDocument(documentId.value)
    createEditor()
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

watch(canEdit, editable => {
  editor.value?.setEditable(editable)
  touchEditorState()
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

.editor-card-header h2 {
  margin: 4px 0 0;
  font-size: 16px;
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

.editor-state i.dirty {
  background: #e5962f;
  box-shadow: 0 0 0 4px #fff2df;
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
  background:
    linear-gradient(#eef1f5 1px, transparent 1px) 0 53px / 100% 32px,
    #fff;
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

  :deep(.document-prose) {
    padding: 26px 20px 60px;
  }
}
</style>