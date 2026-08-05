<template>
  <Teleport to="body">
    <div
      v-if="row"
      ref="backdrop"
      class="document-preview-backdrop"
      role="presentation"
      tabindex="-1"
      @pointerdown.self="$emit('close')"
      @keydown.esc="$emit('close')"
    >
      <section
        class="document-preview-modal"
        role="dialog"
        aria-modal="true"
        :aria-labelledby="titleId"
      >
        <header>
          <div class="document-preview-type">
            <i :class="section">{{ info.icon }}</i>
            <span>
              <small>{{ info.eyebrow }}</small>
              <b>{{ info.name }}</b>
            </span>
          </div>
          <button
            class="document-preview-close"
            type="button"
            aria-label="문서 미리보기 닫기"
            @click="$emit('close')"
          >
            ×
          </button>
        </header>

        <div class="document-preview-heading">
          <div>
            <span v-if="displayStatus">{{ displayStatus }}</span>
            <h2 :id="titleId">{{ displayTitle }}</h2>
            <p>{{ preview.summary }}</p>
          </div>
          <div class="document-preview-status">
            <i></i>
            최신 상태
          </div>
        </div>

        <dl class="document-preview-meta">
          <div>
            <dt>관련 회의</dt>
            <dd>{{ displayMeetingName }}</dd>
          </div>
          <div v-if="!isReportRow">
            <dt>{{ section === 'documents' ? '참여자' : '회의 정보' }}</dt>
            <dd>{{ row[2] }}</dd>
          </div>
          <div>
            <dt>최근 업데이트</dt>
            <dd>{{ displayUpdatedAt }}</dd>
          </div>
        </dl>

        <div class="document-preview-body">
          <article
            v-for="content in preview.sections"
            :key="content.title"
          >
            <h3>{{ content.title }}</h3>
            <p>{{ content.body }}</p>
            <ul v-if="content.items?.length">
              <li v-for="item in content.items" :key="item">{{ item }}</li>
            </ul>
          </article>
        </div>

        <footer>
          <div v-if="preview.tags?.length" class="document-preview-tags">
            <span v-for="tag in preview.tags" :key="tag"># {{ tag }}</span>
          </div>
          <button class="outline-btn" type="button" @click="$emit('close')">
            닫기
          </button>
          <button class="primary" type="button" @click="$emit('open-original')">
            원문 열기 →
          </button>
        </footer>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'

const props = defineProps({
  row: { type: [Array, Object], default: null },
  section: { type: String, default: 'documents' },
  info: { type: Object, required: true }
})

defineEmits(['close', 'open-original'])

const backdrop = ref(null)
const titleId = `document-preview-${Math.random().toString(36).slice(2, 9)}`
const isReportRow = computed(() => props.row && !Array.isArray(props.row))
const displayTitle = computed(() => isReportRow.value ? props.row.title : props.row?.[0])
const displayStatus = computed(() => isReportRow.value ? props.row.statusLabel : props.row?.[4])
const displayMeetingName = computed(() => isReportRow.value ? props.row.meetingName || '-' : props.row?.[1])
const displayUpdatedAt = computed(() => isReportRow.value ? props.row.updatedAt : props.row?.[3])
const preview = computed(() => (isReportRow.value ? props.row.preview : props.row?.[5]) || {
  summary: '선택한 항목의 상세 내용을 확인할 수 있는 임시 미리보기입니다.',
  sections: [
    {
      title: '미리보기',
      body: '실제 문서 API가 연결되면 이 영역에 저장된 본문과 변경 이력이 표시됩니다.'
    }
  ],
  tags: [props.info.name]
})

watch(
  () => props.row,
  async row => {
    if (!row) return
    await nextTick()
    backdrop.value?.focus()
  }
)
</script>
