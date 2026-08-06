<template>
  <TeamLayout :active-section="section" :retry="reloadBoard">
    <header class="page-heading archive-heading">
      <div>
        <span class="eyebrow">{{ eyebrow }}</span>
        <h1>{{ info.name }}</h1>
        <p>{{ info.description }}</p>
      </div>
      <button
        v-if="!isReportSection"
        class="primary"
        type="button"
        @click="notify('새 문서 작성 기능은 API 연결 후 제공됩니다.')"
      >
        ＋ 새 문서
      </button>
    </header>
    <section v-if="!isReportSection" class="archive-stats">
      <article>
        <span>전체 문서</span><b>{{ stats.total }}</b
        ><small>이번 주 +{{ stats.weeklyChange }}</small>
      </article>
      <article>
        <span>{{ stats.secondaryLabel }}</span
        ><b>{{ stats.secondaryValue }}</b
        ><small>{{ stats.secondaryDetail }}</small>
      </article>
      <article>
        <span>{{ stats.tertiaryLabel }}</span
        ><b>{{ stats.tertiaryValue }}</b
        ><small>{{ stats.tertiaryDetail }}</small>
      </article>
    </section>
    <section class="archive-panel">
      <div class="archive-tools" :class="{ 'report-tools': isReportSection }">
        <div v-if="!isReportSection" class="filter-tabs">
          <button
            v-for="tab in tabs"
            :key="tab"
            type="button"
            :class="{ active: activeTab === tab }"
            @click="activeTab = tab"
          >
            {{ tab }}
          </button>
        </div>
        <label>⌕<input v-model.trim="query" :placeholder="`${info.name} 검색`" /></label>
        <AppSelect
          v-model="sort"
          class="archive-sort"
          :options="sortOptions"
          aria-label="목록 정렬"
        />
      </div>
      <div class="archive-list">
        <article
          v-for="row in paginatedRows"
          :key="isReportSection ? `${section}-${row.id}` : `${row[0]}-${row[3]}`"
          class="archive-row"
          :class="{ 'report-row': isReportSection, [section]: isReportSection }"
          role="button"
          tabindex="0"
          @click="openPreview(row)"
          @keydown.enter="openPreview(row)"
        >
          <i class="archive-icon" :class="section">{{ info.icon }}</i>
          <template v-if="isReportSection">
            <div class="archive-title">
              <b>{{ row.title }}</b>
              <span v-if="row.meetingName">{{ row.meetingName }}</span>
            </div>
            <div class="archive-report-date">
              <small>생성일</small><b>{{ row.updatedAt }}</b>
            </div>
            <em v-if="section === 'summary'" :class="{ pending: !row.confirmed }">
              {{ row.confirmed ? '확정 완료' : '검토 필요' }}
            </em>
          </template>
          <template v-else>
            <div class="archive-title">
              <b>{{ row[0] }}</b
              ><span>{{ row[1] }}</span>
            </div>
            <div>
              <small>{{ section === 'documents' ? '편집자' : '회의 정보' }}</small
              ><b>{{ row[2] }}</b>
            </div>
            <div>
              <small>업데이트</small><b>{{ row[3] }}</b>
            </div>
            <em>{{ row[4] }}</em>
          </template>
          <button type="button" @click.stop="openPreview(row)">열기 →</button>
        </article>
      </div>
      <footer class="archive-footer">
        <span>총 {{ totalElements }}개의 항목</span>
        <nav v-if="totalPages > 1" class="archive-pagination" aria-label="문서 목록 페이지">
          <button type="button" :disabled="currentPage === 1" @click="currentPage -= 1">‹</button>
          <button
            v-for="page in totalPages"
            :key="page"
            type="button"
            :class="{ active: currentPage === page }"
            :aria-current="currentPage === page ? 'page' : undefined"
            @click="currentPage = page"
          >
            {{ page }}
          </button>
          <button type="button" :disabled="currentPage === totalPages" @click="currentPage += 1">
            ›
          </button>
        </nav>
      </footer>
    </section>
    <DocumentPreviewModal
      v-if="!isReportSection"
      :row="selectedRow"
      :section="section"
      :info="{ ...info, eyebrow }"
      @close="selectedRow = null"
      @open-original="openOriginal"
    />
    <ReportDetailModal
      v-else
      :report="selectedRow"
      :saving="savingReport"
      @close="selectedRow = null"
      @save="saveMinutes"
      @confirm="confirmMinutes"
      @export="exportReport"
    />
  </TeamLayout>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AppSelect from '../../../shared/components/AppSelect.vue'
import { useToast } from '../../../shared/composables/useToast'
import DocumentPreviewModal from '../components/DocumentPreviewModal.vue'
import ReportDetailModal from '../components/ReportDetailModal.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const props = defineProps({
  section: { type: String, default: 'documents' }
})
const archiveEyebrows = {
  documents: 'SHARED DOCUMENTS',
  minutes: 'MEETING MINUTES',
  summary: 'AI SUMMARY',
  feedback: 'AI 퍼실리테이터 피드백'
}
const archiveInfo = {
  documents: {
    name: '공유 문서',
    icon: '▤',
    description: '팀원과 함께 작성 중인 문서를 확인하고 편집하세요.'
  },
  minutes: {
    name: '회의록',
    icon: '▤',
    description: '회의별 발언과 결정사항이 정리된 회의록입니다.'
  },
  summary: {
    name: 'AI 요약',
    icon: 'AI',
    description: 'AI가 회의의 핵심 내용과 액션 아이템을 정리했습니다.'
  },
  feedback: {
    name: 'AI 피드백',
    icon: '✦',
    description: 'AI 퍼실리테이터가 분석한 회의 품질과 진행 방식 개선 제안을 확인하세요.'
  }
}
const fallbackStats = {
  total: 0,
  weeklyChange: 0,
  secondaryLabel: '최근 업데이트',
  secondaryValue: '-',
  secondaryDetail: '',
  tertiaryLabel: '팀 공유',
  tertiaryValue: '-',
  tertiaryDetail: ''
}
const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team', 'archive'],
  section: () => props.section
})
const { notify } = useToast()
const tabs = ['전체', '최근 열어본', '내 문서']
const activeTab = ref('전체')
const query = ref('')
const sort = ref('recent')
const sortOptions = [
  { value: 'recent', label: '최근 수정 순' },
  { value: 'name', label: '이름 순' }
]
const selectedRow = ref(null)
const savingReport = ref(false)
const pageSize = 10
const currentPage = ref(1)
const isReportSection = computed(() => ['minutes', 'summary', 'feedback'].includes(props.section))
const eyebrow = computed(() => archiveEyebrows[props.section] || archiveEyebrows.documents)
const info = computed(() => archiveInfo[props.section] || archiveInfo.documents)
const rows = computed(() => boardState.archives[props.section] || [])
const stats = computed(
  () => boardState.archiveStats[props.section] || { ...fallbackStats, total: rows.value.length }
)
const visibleRows = computed(() => {
  const keyword = query.value.toLowerCase()
  const filtered = rows.value.filter((row) => {
    const values = Array.isArray(row) ? row : Object.values(row)
    return values.join(' ').toLowerCase().includes(keyword)
  })
  return sort.value === 'name'
    ? [...filtered].sort((a, b) => (a.title || a[0]).localeCompare(b.title || b[0]))
    : filtered
})
const reportPagination = computed(() => boardState.archivePagination[props.section])
const totalElements = computed(
  () => reportPagination.value?.totalElements ?? visibleRows.value.length
)
const totalPages = computed(() =>
  isReportSection.value
    ? Math.max(1, reportPagination.value?.totalPages || 1)
    : Math.max(1, Math.ceil(visibleRows.value.length / pageSize))
)
const paginatedRows = computed(() => {
  if (isReportSection.value) return visibleRows.value
  const start = (currentPage.value - 1) * pageSize
  return visibleRows.value.slice(start, start + pageSize)
})

watch([query, sort, () => props.section], () => {
  currentPage.value = 1
})

watch(totalPages, (pageCount) => {
  if (currentPage.value > pageCount) currentPage.value = pageCount
})

watch(currentPage, async (page) => {
  if (!isReportSection.value || !teamId.value) return
  await boardStore.loadArchive(teamId.value, props.section, page - 1, pageSize)
})

async function openPreview(row) {
  if (!isReportSection.value) {
    selectedRow.value = row
    return
  }

  try {
    selectedRow.value = await boardStore.loadReportDetail(row.id, props.section, row)
  } catch (error) {
    notify(error.message || '상세 내용을 불러오지 못했습니다.')
  }
}

function openOriginal() {
  if (!selectedRow.value) return
  notify(`${selectedRow.value.title || selectedRow.value[0]} 내용을 확인했습니다.`)
}

async function saveMinutes(draft) {
  if (!selectedRow.value || selectedRow.value.confirmed) return
  savingReport.value = true
  try {
    selectedRow.value = await boardStore.updateMinutes(
      selectedRow.value.id,
      draft,
      selectedRow.value
    )
    notify('AI 요약 수정 내용을 저장했습니다.')
  } catch (error) {
    notify(error.message || 'AI 요약을 저장하지 못했습니다.')
  } finally {
    savingReport.value = false
  }
}

async function confirmMinutes() {
  if (!selectedRow.value || selectedRow.value.confirmed) return
  const approved = window.confirm(
    'AI 요약을 최종 확정하면 더 이상 수정할 수 없습니다. 확정하시겠습니까?'
  )
  if (!approved) return

  savingReport.value = true
  try {
    const result = await boardStore.confirmMinutes(selectedRow.value.id)
    selectedRow.value = {
      ...selectedRow.value,
      confirmed: Boolean(result?.isConfirmed),
      confirmedAt: result?.confirmedAt || null
    }
    notify('AI 요약을 최종 확정했습니다.')
  } catch (error) {
    notify(error.message || 'AI 요약을 확정하지 못했습니다.')
  } finally {
    savingReport.value = false
  }
}

async function exportReport(format) {
  if (!selectedRow.value) return
  try {
    const exporter =
      selectedRow.value.kind === 'transcript'
        ? boardStore.exportTranscript
        : selectedRow.value.kind === 'facilitator'
          ? boardStore.exportFacilitatorReport
          : boardStore.exportMinutes
    const result = await exporter(selectedRow.value.id, format)
    if (!result?.url) throw new Error('내보내기 파일 URL을 받지 못했습니다.')
    const link = document.createElement('a')
    link.href = result.url
    link.target = '_blank'
    link.rel = 'noopener noreferrer'
    link.click()
    notify(`${format.toUpperCase()} 파일 내보내기를 시작했습니다.`)
  } catch (error) {
    notify(error.message || '회의 전문을 내보내지 못했습니다.')
  }
}
</script>

<style scoped>
.archive-pagination {
  display: flex;
  align-items: center;
  gap: 4px;
}

.archive-pagination button:disabled {
  cursor: default;
  opacity: 0.45;
}

.archive-tools.report-tools label {
  margin-left: auto;
}

.archive-row.report-row.minutes {
  grid-template-columns: 40px minmax(0, 2fr) minmax(180px, 1fr) 64px;
}

.archive-row.report-row.summary {
  grid-template-columns: 40px minmax(0, 2fr) minmax(180px, 1fr) 88px 64px;
}

.archive-row.report-row.feedback {
  grid-template-columns: 40px minmax(0, 2fr) minmax(180px, 1fr) 64px;
}

.archive-report-date {
  min-width: 0;
}

.archive-row em.pending {
  background: #fff4df;
  color: #a56612;
}
</style>
