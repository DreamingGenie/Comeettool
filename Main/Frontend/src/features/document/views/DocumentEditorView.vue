<template>
  <TeamLayout active-section="documents" :retry="reloadBoard">
    <header class="page-heading document-editor-heading">
      <div>
        <span class="eyebrow">SHARED DOCUMENT</span>
        <h1>{{ currentTitle }}</h1>
        <p>문서 편집 화면은 다음 단계에서 연결됩니다.</p>
      </div>
      <button class="outline-btn" type="button" @click="goBack">
        문서 목록
      </button>
    </header>
    <section class="document-editor-placeholder">
      <i>▤</i>
      <h2>문서가 생성되었습니다.</h2>
      <p>
        문서 ID {{ documentId }}의 Tiptap 편집기는 3단계에서 구성합니다.
      </p>
    </section>
  </TeamLayout>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import TeamLayout from '../../board/components/TeamLayout.vue'
import { useBoardPage } from '../../board/composables/useBoardPage'
import { documentStore } from '../stores/documentStore'

const route = useRoute()
const router = useRouter()
const documentId = computed(() => String(route.params.documentId || ''))
const currentTitle = computed(() =>
  documentStore.state.currentDocument?.documentId === documentId.value
    ? documentStore.state.currentDocument.title
    : '공유 문서'
)
const { teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team']
})

function goBack() {
  router.push({
    name: 'team-documents',
    params: { teamId: teamId.value }
  })
}
</script>

<style scoped>
.document-editor-heading{margin-bottom:18px}
.document-editor-placeholder{display:grid;place-items:center;min-height:420px;padding:40px;border:1px dashed #cfd5e2;border-radius:14px;background:#fff;text-align:center}
.document-editor-placeholder i{display:grid;place-items:center;width:64px;height:64px;border-radius:18px;background:#edf1ff;color:var(--blue);font-size:24px;font-style:normal}
.document-editor-placeholder h2{margin:18px 0 6px}
.document-editor-placeholder p{max-width:520px;margin:0;color:#697287;line-height:1.7;word-break:break-all}
</style>