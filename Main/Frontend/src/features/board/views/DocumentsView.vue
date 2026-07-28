<template>
  <TeamLayout :active-section="section">
    <header class="page-heading archive-heading">
      <div>
        <span class="eyebrow">MEETING ARCHIVE</span>
        <h1>{{ info.name }}</h1>
        <p>{{ info.description }}</p>
      </div>
      <button class="primary" type="button" @click="notify('새 문서 작성 기능은 API 연결 후 제공됩니다.')">
        ＋ 새 문서
      </button>
    </header>
    <section class="archive-stats">
      <article><span>전체 문서</span><b>{{ stats.total }}</b><small>이번 주 +{{ stats.weeklyChange }}</small></article>
      <article><span>{{ stats.secondaryLabel }}</span><b>{{ stats.secondaryValue }}</b><small>{{ stats.secondaryDetail }}</small></article>
      <article><span>{{ stats.tertiaryLabel }}</span><b>{{ stats.tertiaryValue }}</b><small>{{ stats.tertiaryDetail }}</small></article>
    </section>
    <section class="archive-panel">
      <div class="archive-tools">
        <div class="filter-tabs">
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
        <select v-model="sort">
          <option value="recent">최근 수정 순</option>
          <option value="name">이름 순</option>
        </select>
      </div>
      <div class="archive-list">
        <article v-for="row in visibleRows" :key="`${row[0]}-${row[3]}`" class="archive-row">
          <i class="archive-icon" :class="section">{{ info.icon }}</i>
          <div class="archive-title"><b>{{ row[0] }}</b><span>{{ row[1] }}</span></div>
          <div><small>{{ section === 'documents' ? '편집자' : '회의 정보' }}</small><b>{{ row[2] }}</b></div>
          <div><small>업데이트</small><b>{{ row[3] }}</b></div>
          <em>{{ row[4] }}</em>
          <button type="button" @click="notify(`${row[0]} 항목을 열었습니다.`)">열기 →</button>
        </article>
      </div>
      <footer class="archive-footer">
        <span>총 {{ visibleRows.length }}개의 항목</span>
        <div><button disabled>‹</button><button class="active">1</button><button disabled>›</button></div>
      </footer>
    </section>
  </TeamLayout>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useToast } from '../../../shared/composables/useToast'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'

const props = defineProps({
  section: { type: String, default: 'documents' }
})
const archiveInfo = {
  documents: { name: '공유 문서', icon: '▤', description: '팀원과 함께 작성 중인 문서를 확인하고 편집하세요.' },
  minutes: { name: '회의록', icon: '▤', description: '회의별 발언과 결정사항이 정리된 회의록입니다.' },
  summary: { name: 'AI 요약', icon: 'AI', description: 'AI가 회의의 핵심 내용과 액션 아이템을 정리했습니다.' },
  feedback: { name: 'AI 피드백', icon: '✦', description: 'AI가 분석한 회의 품질과 개선 제안을 확인하세요.' }
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
const { boardState } = useBoardPage()
const { notify } = useToast()
const tabs = ['전체', '최근 열어본', '내 문서']
const activeTab = ref('전체')
const query = ref('')
const sort = ref('recent')
const info = computed(() => archiveInfo[props.section] || archiveInfo.documents)
const rows = computed(() => boardState.archives[props.section] || [])
const stats = computed(() => boardState.archiveStats[props.section] || { ...fallbackStats, total: rows.value.length })
const visibleRows = computed(() => {
  const keyword = query.value.toLowerCase()
  const filtered = rows.value.filter(row => row.join(' ').toLowerCase().includes(keyword))
  return sort.value === 'name'
    ? [...filtered].sort((a, b) => a[0].localeCompare(b[0]))
    : filtered
})
</script>
