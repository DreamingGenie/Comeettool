<template>
  <TeamLayout active-section="documents" :retry="reloadBoard">
    <header class="page-heading document-heading">
      <div>
        <span class="eyebrow">SHARED DOCUMENTS</span>
        <h1>공유 문서</h1>
        <p>팀원과 함께 작성 중인 문서를 확인하고 편집하세요.</p>
      </div>
      <div class="document-heading-actions">
        <span v-if="isGuest" class="read-only-badge">읽기 전용</span>
        <button
          v-if="canCreate"
          class="primary"
          type="button"
          :disabled="documentState.creating"
          @click="createDocument"
        >
          {{ documentState.creating ? '생성 중...' : '＋ 새 문서' }}
        </button>
      </div>
    </header>

    <section class="document-stats">
      <article>
        <span>전체 문서</span>
        <b>{{ documentState.documents.length }}</b>
        <small>삭제되지 않은 공유 문서</small>
      </article>
      <article>
        <span>최근 업데이트</span>
        <b>{{ latestUpdatedAt }}</b>
        <small>{{ latestDocumentTitle }}</small>
      </article>
      <article>
        <span>내 권한</span>
        <b>{{ authorityLabel }}</b>
        <small>{{ permissionDescription }}</small>
      </article>
    </section>

    <section class="document-panel">
      <div class="document-tools">
        <label class="document-search">
          <span>⌕</span>
          <input
            v-model.trim="query"
            type="search"
            placeholder="문서 제목 검색"
          />
        </label>
        <select v-model="sort">
          <option value="recent">최근 수정 순</option>
          <option value="name">이름 순</option>
        </select>
      </div>

      <AsyncState
        v-if="
          documentState.loadingList ||
          documentState.error ||
          visibleDocuments.length === 0
        "
        :loading="documentState.loadingList"
        :error="documentState.error"
        :empty="
          !documentState.loadingList &&
          !documentState.error &&
          visibleDocuments.length === 0
        "
        :retry="loadDocuments"
      />

      <div v-else class="document-list">
        <article
          v-for="document in visibleDocuments"
          :key="document.documentId"
          class="document-row"
          role="button"
          tabindex="0"
          @click="openDocument(document)"
          @keydown.enter="openDocument(document)"
        >
          <i class="document-icon">▤</i>
          <div class="document-title">
            <b>{{ document.title }}</b>
            <span>{{ document.documentId }}</span>
          </div>
          <div class="document-meta">
            <small>최종 버전</small>
            <b>v{{ document.finalVersion }}</b>
          </div>
          <div class="document-meta">
            <small>업데이트</small>
            <b>{{ formatDate(document.updatedAt) }}</b>
          </div>
          <div class="document-row-actions">
            <button
              v-if="canDelete"
              class="delete-button"
              type="button"
              :aria-label="`${document.title} 삭제`"
              @click.stop="requestDelete(document)"
            >
              삭제
            </button>
            <button
              class="open-button"
              type="button"
              @click.stop="openDocument(document)"
            >
              열기 →
            </button>
          </div>
        </article>
      </div>

      <footer class="document-footer">
        총 {{ visibleDocuments.length }}개의 문서
      </footer>
    </section>

    <BaseModal
      v-if="deleteTarget"
      modal-class="document-delete-modal"
      @close="closeDeleteModal"
    >
      <span class="delete-modal-eyebrow">DELETE DOCUMENT</span>
      <h2>문서를 삭제할까요?</h2>
      <p>
        <strong>{{ deleteTarget.title }}</strong>
        문서는 삭제 후 목록과 실시간 편집에서 사용할 수 없습니다.
      </p>
      <div class="delete-modal-actions">
        <button
          class="outline-btn"
          type="button"
          :disabled="isDeleting"
          @click="closeDeleteModal"
        >
          취소
        </button>
        <button
          class="confirm-delete-button"
          type="button"
          :disabled="isDeleting"
          @click="confirmDelete"
        >
          {{ isDeleting ? '삭제 중...' : '삭제' }}
        </button>
      </div>
    </BaseModal>
  </TeamLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AsyncState from '../../../shared/components/AsyncState.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import TeamLayout from '../../board/components/TeamLayout.vue'
import { useBoardPage } from '../../board/composables/useBoardPage'
import { documentStore } from '../stores/documentStore'

const router = useRouter()
const { notify } = useToast()
const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team']
})
const documentState = documentStore.state
const query = ref('')
const sort = ref('recent')
const deleteTarget = ref(null)

const authority = computed(() =>
  String(boardState.team.role || '').toUpperCase()
)
const canCreate = computed(() =>
  authority.value === 'OWNER' || authority.value === 'MEMBER'
)
const canDelete = computed(() => authority.value === 'OWNER')
const isGuest = computed(() => authority.value === 'GUEST')
const authorityLabel = computed(() => {
  if (authority.value === 'OWNER') return 'Owner'
  if (authority.value === 'MEMBER') return 'Member'
  if (authority.value === 'GUEST') return 'Guest'
  return '-'
})
const permissionDescription = computed(() => {
  if (authority.value === 'OWNER') return '생성·편집·삭제 가능'
  if (authority.value === 'MEMBER') return '생성·편집 가능'
  if (authority.value === 'GUEST') return '조회만 가능'
  return '권한 확인 중'
})
const latestDocument = computed(() => documentState.documents[0] || null)
const latestUpdatedAt = computed(() =>
  latestDocument.value ? formatDate(latestDocument.value.updatedAt) : '-'
)
const latestDocumentTitle = computed(() =>
  latestDocument.value?.title || '아직 문서가 없습니다.'
)
const visibleDocuments = computed(() => {
  const keyword = query.value.toLocaleLowerCase()
  const filtered = documentState.documents.filter(document =>
    document.title.toLocaleLowerCase().includes(keyword)
  )

  if (sort.value === 'name') {
    return [...filtered].sort((a, b) => a.title.localeCompare(b.title, 'ko'))
  }

  return [...filtered].sort(
    (a, b) => new Date(b.updatedAt) - new Date(a.updatedAt)
  )
})
const isDeleting = computed(() =>
  Boolean(
    deleteTarget.value &&
    documentState.deletingDocumentId === deleteTarget.value.documentId
  )
)

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

async function loadDocuments() {
  try {
    await documentStore.loadDocumentList(teamId.value)
  } catch {
    // 오류 문구와 재시도는 AsyncState에서 처리한다.
  }
}

async function createDocument() {
  if (!canCreate.value) return

  try {
    const document = await documentStore.createDocument(teamId.value)
    notify('새 문서가 생성되었습니다.')
    await router.push({
      name: 'team-document-editor',
      params: {
        teamId: teamId.value,
        documentId: document.documentId
      }
    })
  } catch (error) {
    notify(error?.message || '문서를 생성하지 못했습니다.')
    documentStore.clearError()
  }
}

function openDocument(document) {
  router.push({
    name: 'team-document-editor',
    params: {
      teamId: teamId.value,
      documentId: document.documentId
    }
  })
}

function requestDelete(document) {
  if (!canDelete.value) return
  deleteTarget.value = document
}

function closeDeleteModal() {
  if (isDeleting.value) return
  deleteTarget.value = null
}

async function confirmDelete() {
  if (!deleteTarget.value || !canDelete.value) return

  try {
    await documentStore.deleteDocument(deleteTarget.value.documentId)
    notify('문서가 삭제되었습니다.')
    deleteTarget.value = null
  } catch (error) {
    notify(error?.message || '문서를 삭제하지 못했습니다.')
    documentStore.clearError()
  }
}

onMounted(loadDocuments)
watch(teamId, loadDocuments)
</script>

<style scoped>
.document-heading{margin-bottom:18px}
.document-heading-actions{display:flex;align-items:center;gap:10px}
.document-heading-actions .primary:disabled{cursor:wait;opacity:.65}
.read-only-badge{padding:7px 11px;border-radius:18px;background:#eef1f7;color:#697287;font-size:10px;font-weight:700}
.document-stats{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:14px;margin-bottom:16px}
.document-stats article{display:grid;grid-template-columns:1fr auto;align-items:end;min-height:82px;padding:15px 18px;border:1px solid var(--line);border-radius:11px;background:#fff}
.document-stats span{color:var(--muted);font-size:10px}
.document-stats b{grid-row:1/3;grid-column:2;max-width:190px;overflow:hidden;font-size:17px;text-overflow:ellipsis;white-space:nowrap}
.document-stats small{margin-top:5px;color:#7d8596;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.document-panel{overflow:hidden;border:1px solid var(--line);border-radius:12px;background:#fff}
.document-tools{display:flex;justify-content:flex-end;gap:9px;min-height:58px;padding:10px 16px;border-bottom:1px solid var(--line)}
.document-search{display:flex;align-items:center;width:min(320px,100%);padding:7px 10px;border:1px solid var(--line);border-radius:7px;color:#8a92a2}
.document-search input{flex:1;min-width:0;margin-left:6px;border:0;outline:0}
.document-tools select{padding:7px 10px;border:1px solid var(--line);border-radius:7px;background:#fff}
.document-list{min-height:80px}
.document-row{display:grid;grid-template-columns:42px minmax(180px,2fr) minmax(80px,.6fr) minmax(160px,1fr) auto;gap:12px;align-items:center;min-height:72px;padding:9px 16px;border-bottom:1px solid #e3e6ed;outline:0;transition:background .15s ease}
.document-row:hover,.document-row:focus-visible{background:#f8f9ff}
.document-icon{display:grid;place-items:center;width:35px;height:35px;border-radius:9px;background:#edf1ff;color:var(--blue);font-style:normal;font-weight:800}
.document-title,.document-meta{display:grid;gap:3px;min-width:0}
.document-title b{overflow:hidden;font-size:12px;text-overflow:ellipsis;white-space:nowrap}
.document-title span{overflow:hidden;color:#8a92a2;font-size:8px;text-overflow:ellipsis;white-space:nowrap}
.document-meta small{color:#8a92a2;font-size:9px}
.document-meta b{font-size:10px}
.document-row-actions{display:flex;align-items:center;gap:5px}
.document-row-actions button{padding:6px 8px;border:0;border-radius:6px;background:transparent;font-size:10px;font-weight:700}
.open-button{color:var(--blue2)}
.delete-button{color:#c83a3a}
.delete-button:hover{background:#fff0f0}
.open-button:hover{background:#eef1ff}
.document-footer{height:48px;padding:16px;background:#f5f6f9;color:#666f80;font-size:10px}
:deep(.async-state){min-height:230px}
:deep(.document-delete-modal){width:min(430px,calc(100vw - 24px));padding:38px 30px 28px}
.delete-modal-eyebrow{color:#cf3c3c;font-size:9px;font-weight:800;letter-spacing:1px}
.document-delete-modal h2{margin:7px 0 12px}
.document-delete-modal p{margin:0;color:#697287;line-height:1.7}
.document-delete-modal p strong{display:block;color:var(--text)}
.delete-modal-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:26px}
.delete-modal-actions button:disabled{cursor:wait;opacity:.6}
.confirm-delete-button{padding:8px 17px;border:0;border-radius:7px;background:#cf3c3c;color:#fff}
@media(max-width:900px){
  .document-row{grid-template-columns:38px minmax(160px,1fr) minmax(130px,.7fr) auto}
  .document-row>.document-meta:first-of-type{display:none}
}
@media(max-width:720px){
  .document-heading{align-items:flex-start;gap:12px}
  .document-stats{grid-template-columns:1fr}
  .document-tools{align-items:stretch;flex-direction:column}
  .document-search{width:100%}
  .document-row{grid-template-columns:38px minmax(0,1fr) auto}
  .document-row>.document-meta{display:none}
}

/* 코밋툴 공통 80% 스케일에 맞춘 문서 화면 */
.document-heading {
  min-height: 54px;
  margin-bottom: 19.2px;
  font-family: 'Noto Sans KR', sans-serif;
}

.document-heading h1 {
  margin: 4.8px 0 2.4px;
  font-size: 21.6px;
  line-height: 1.3;
  letter-spacing: -0.8px;
}

.document-heading p {
  font-size: 11.2px;
  line-height: 1.55;
}

.document-heading-actions .primary {
  min-width: 104px;
  min-height: 40px;
  padding: 8px 16px;
  border-radius: 7.2px;
  font-size: 11.2px;
}

.document-stats {
  gap: 16px;
  margin-bottom: 19.2px;
}

.document-stats article {
  grid-template-rows: auto auto;
  align-content: center;
  align-items: center;
  row-gap: 12px;
  min-height: 82px;
  padding: 14px 20px;
  border-width: 0.8px;
  border-radius: 12px;
}

.document-stats span {
  grid-row: 1;
  grid-column: 1 / 3;
  font-size: 13.6px;
  font-weight: 800;
  line-height: 1.3;
  letter-spacing: -0.25px;
}

.document-stats small {
  grid-row: 2;
  grid-column: 1;
  font-size: 10.4px;
  line-height: 1.45;
}

.document-stats b {
  grid-row: 2;
  grid-column: 2;
  align-self: center;
  max-width: 240px;
  font-size: 16px;
  line-height: 1.35;
}

.document-panel {
  border-width: 0.8px;
  border-radius: 12px;
  font-family: 'Noto Sans KR', sans-serif;
}

.document-tools {
  min-height: 64px;
  gap: 9.6px;
  padding: 12px 16px;
}

.document-search {
  width: min(360px, 100%);
  min-height: 40px;
  padding: 8px 12px;
  border-width: 0.8px;
  border-radius: 7.2px;
  font-size: 12.8px;
}

.document-search input {
  font-size: 12.8px;
}

.document-tools select {
  min-width: 120px;
  min-height: 40px;
  padding: 8px 12px;
  border-width: 0.8px;
  border-radius: 7.2px;
  font-size: 12.8px;
}

.document-row {
  grid-template-columns:
    40px minmax(200px, 2fr) minmax(72px, 0.55fr)
    minmax(170px, 0.9fr) auto;
  gap: 14.4px;
  min-height: 80px;
  padding: 12px 16px;
  border-bottom-width: 0.8px;
}

.document-icon {
  width: 36.8px;
  height: 36.8px;
  border-radius: 9.6px;
  font-size: 12px;
}

.document-title {
  gap: 4px;
}

.document-title b {
  font-size: 14px;
  line-height: 1.4;
}

.document-title span {
  font-size: 10.4px;
  line-height: 1.4;
}

.document-meta {
  gap: 4px;
}

.document-meta small {
  font-size: 10.4px;
}

.document-meta b {
  overflow: hidden;
  font-size: 12px;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.document-row-actions {
  gap: 4.8px;
}

.document-row-actions button {
  min-height: 30.4px;
  padding: 6.4px 9.6px;
  border-radius: 5.6px;
  font-size: 11.2px;
}

.document-footer {
  display: flex;
  align-items: center;
  height: 52.8px;
  padding: 0 16px;
  font-size: 11.2px;
}

@media (max-width: 1100px) {
  .document-stats b {
    max-width: 160px;
  }

  .document-row {
    grid-template-columns:
      40px minmax(170px, 1.6fr) minmax(64px, 0.45fr)
      minmax(145px, 0.8fr) auto;
    gap: 10px;
  }
}
</style>
