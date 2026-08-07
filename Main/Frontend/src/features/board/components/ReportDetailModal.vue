<template>
  <Teleport to="body">
    <div v-if="report" class="report-modal-backdrop" @pointerdown.self="$emit('close')">
      <section class="report-modal" role="dialog" aria-modal="true" aria-labelledby="report-title">
        <header class="report-modal-header">
          <div>
            <span>{{ reportTypeLabel }}</span>
            <h2 id="report-title">{{ isTranscript || isFeedback ? report.title : draft.title }}</h2>
            <p>{{ report.meetingName || report.title }} · {{ report.updatedAt }}</p>
          </div>
          <button type="button" aria-label="닫기" @click="$emit('close')">×</button>
        </header>

        <div v-if="isTranscript" class="report-content transcript-content">
          <div class="report-toolbar">
            <strong>회의 전문</strong>
            <div>
              <button type="button" @click="$emit('export', 'md')">MD 내보내기</button>
              <button type="button" @click="$emit('export', 'pdf')">PDF 내보내기</button>
            </div>
          </div>
          <ol v-if="report.transcript?.length" class="transcript-list">
            <li v-for="segment in report.transcript" :key="segment.id">
              <div class="speaker-avatar">{{ segment.speaker.slice(0, 1).toUpperCase() }}</div>
              <article>
                <div>
                  <b>{{ segment.speaker }}</b>
                  <time v-if="segment.start !== null"
                    >{{ formatTime(segment.start)
                    }}<template v-if="segment.end !== null">
                      – {{ formatTime(segment.end) }}</template
                    ></time
                  >
                </div>
                <p>{{ segment.text }}</p>
              </article>
            </li>
          </ol>
          <p v-else class="report-empty">표시할 발화 내용이 없습니다.</p>
        </div>

        <div v-else-if="isFeedback" class="report-content feedback-content">
          <div class="report-toolbar">
            <div>
              <strong v-if="feedbackMeetingType">{{ feedbackMeetingType }}</strong>
              <p>{{ report.overallReview || '종합 평가 내용이 없습니다.' }}</p>
            </div>
            <div>
              <button type="button" @click="$emit('export', 'md')">마크다운 내보내기</button>
              <button type="button" @click="$emit('export', 'pdf')">PDF 내보내기</button>
            </div>
          </div>
          <article v-if="report.participationComment" class="feedback-overview">
            <h3>참여 종합 의견</h3>
            <p>{{ report.participationComment }}</p>
          </article>
          <section class="feedback-grid">
            <article
              v-for="section in feedbackSections"
              :key="section.key"
              class="feedback-card"
              :class="`feedback-card-${section.key}`"
            >
              <h3>{{ section.label }}</h3>
              <div
                v-if="section.key === 'participationStats' && Array.isArray(section.value)"
                class="participant-stat-list"
              >
                <article v-for="(participant, index) in section.value" :key="index">
                  <dl>
                    <div>
                      <dt>참여자</dt>
                      <dd>{{ participantValue(participant, 'speaker') }}</dd>
                    </div>
                    <div>
                      <dt>발화 비중</dt>
                      <dd>{{ formatSpeakingRatio(participantValue(participant, 'ratio')) }}</dd>
                    </div>
                    <div>
                      <dt>발화 횟수</dt>
                      <dd>{{ participantValue(participant, 'utteranceCount') }}회</dd>
                    </div>
                    <div>
                      <dt>발화 시간</dt>
                      <dd>{{ formatSpeakingSeconds(participantValue(participant, 'speakingSeconds')) }}</dd>
                    </div>
                  </dl>
                </article>
              </div>
              <dl
                v-else-if="section.key === 'qualityEvaluation' && isPlainObject(section.value)"
                class="quality-evaluation-list"
              >
                <div v-for="entry in qualityEntries(section.value)" :key="entry.key">
                  <dt>{{ entry.label }}</dt>
                  <dd>{{ entry.grade }}</dd>
                </div>
              </dl>
              <template v-else-if="Array.isArray(section.value)">
                <ul v-if="section.value.length">
                  <li v-for="(item, index) in section.value" :key="index">
                    <dl
                      v-if="isPlainObject(item) && labeledFields(section.key, item).length"
                      class="feedback-item-fields"
                    >
                      <div v-for="field in labeledFields(section.key, item)" :key="field.key">
                        <dt>{{ field.label }}</dt>
                        <dd>{{ displayValue(field.value) }}</dd>
                      </div>
                    </dl>
                    <template v-else>{{ displayValue(item) }}</template>
                  </li>
                </ul>
                <p v-else class="report-empty">분석 내용이 없습니다.</p>
              </template>
              <dl v-else-if="isPlainObject(section.value)">
                <div v-for="(value, key) in section.value" :key="key">
                  <dt>{{ displayKey(key) }}</dt>
                  <dd>{{ displayValue(value) }}</dd>
                </div>
              </dl>
              <p v-else>{{ displayValue(section.value) || '분석 내용이 없습니다.' }}</p>
            </article>
          </section>
        </div>

        <form v-else class="report-content minutes-content" @submit.prevent="save">
          <div class="minutes-state" :class="{ confirmed: report.confirmed }">
            <span>{{ report.confirmed ? '최종 확정' : '검토 및 수정 가능' }}</span>
            <p>
              {{
                report.confirmed
                  ? '확정된 회의록은 수정할 수 없습니다.'
                  : '수정 후 저장하면 배열 항목 전체가 최신 내용으로 교체됩니다.'
              }}
            </p>
          </div>

          <label class="minutes-field">
            <span>제목</span>
            <input v-model="draft.title" :readonly="report.confirmed" maxlength="200" required />
          </label>
          <label class="minutes-field">
            <span>요약</span>
            <textarea v-model="draft.summary" :readonly="report.confirmed" rows="4"></textarea>
          </label>

          <section v-for="group in groups" :key="group.key" class="minutes-group">
            <header>
              <div>
                <h3>{{ group.label }}</h3>
                <span>{{ draft[group.key].length }}개</span>
              </div>
              <button v-if="!report.confirmed" type="button" @click="addItem(group)">
                ＋ 항목 추가
              </button>
            </header>
            <div class="minutes-card-list">
              <article
                v-for="(item, index) in draft[group.key]"
                :key="item._clientId"
                class="minutes-card"
              >
                <span>{{ index + 1 }}</span>
                <div class="minutes-card-fields">
                  <label
                    v-for="field in visibleFields(group, item)"
                    :key="field.key"
                    :class="{ 'full-width': field.full }"
                  >
                    <small>{{ field.label }}</small>
                    <textarea
                      v-if="field.multiline"
                      v-model="item[field.key]"
                      :readonly="report.confirmed"
                      :rows="field.rows || 3"
                      :placeholder="field.placeholder || ''"
                    ></textarea>
                    <input
                      v-else
                      v-model="item[field.key]"
                      :readonly="report.confirmed"
                      :type="field.type || 'text'"
                      :placeholder="field.placeholder || ''"
                    />
                  </label>
                </div>
                <button
                  v-if="!report.confirmed"
                  type="button"
                  aria-label="항목 삭제"
                  @click="removeItem(group.key, index)"
                >
                  ×
                </button>
              </article>
              <p v-if="!draft[group.key].length" class="report-empty">등록된 항목이 없습니다.</p>
            </div>
          </section>

          <footer>
            <template v-if="!report.confirmed">
              <button type="button" class="outline-btn" @click="resetDraft">변경 취소</button>
              <button type="submit" class="outline-btn" :disabled="saving">
                {{ saving ? '저장 중...' : '수정 내용 저장' }}
              </button>
              <button type="button" class="primary" :disabled="saving" @click="$emit('confirm')">
                최종 확정
              </button>
            </template>
            <template v-else>
              <button type="button" class="outline-btn" @click="$emit('export', 'md')">
                MD 내보내기
              </button>
              <button type="button" class="primary" @click="$emit('export', 'pdf')">
                PDF 내보내기
              </button>
            </template>
          </footer>
        </form>
      </section>
    </div>
  </Teleport>
</template>

<script setup>
import { computed, reactive, watch } from 'vue'

const props = defineProps({
  report: { type: Object, default: null },
  saving: { type: Boolean, default: false }
})
const emit = defineEmits(['close', 'save', 'confirm', 'export'])

const groups = [
  {
    key: 'topics',
    label: '주요 안건',
    fields: [
      { key: 'title', label: '안건 제목', full: true },
      { key: 'summary', label: '안건 내용', multiline: true, full: true },
      { key: 'sourceTimestamp', label: '근거 시각', placeholder: '00:00:00' }
    ]
  },
  {
    key: 'decisions',
    label: '결정 사항',
    fields: [
      { key: 'decision', label: '결정 내용', multiline: true, full: true },
      { key: 'rationale', label: '결정 근거', multiline: true, full: true },
      { key: 'owner', label: '담당자' },
      { key: 'sourceTimestamp', label: '근거 시각', placeholder: '00:00:00' }
    ]
  },
  {
    key: 'actionItems',
    label: '후속 할 일',
    fields: [
      { key: 'task', label: '할 일', multiline: true, full: true, rows: 2 },
      { key: 'assignee', label: '담당자' },
      { key: 'dueDate', label: '완료일', type: 'date' },
      { key: 'sourceTimestamp', label: '근거 시각', placeholder: '00:00:00' }
    ]
  },
  {
    key: 'openIssues',
    label: '미해결 이슈',
    fields: [
      { key: 'issue', label: '이슈', multiline: true, full: true, rows: 2 },
      { key: 'sourceTimestamp', label: '근거 시각', placeholder: '00:00:00' }
    ]
  }
]

const draft = reactive({
  title: '',
  summary: '',
  topics: [],
  decisions: [],
  actionItems: [],
  openIssues: []
})
const isTranscript = computed(() => props.report?.kind === 'transcript')
const isFeedback = computed(() => props.report?.kind === 'facilitator')
const reportTypeLabel = computed(() =>
  isTranscript.value
    ? 'MEETING TRANSCRIPT'
    : isFeedback.value
      ? 'AI 퍼실리테이터 회의 피드백'
      : 'AI MEETING SUMMARY'
)
const feedbackMeetingType = computed(() => displayMeetingType(props.report?.meetingType))
const feedbackSections = computed(() => {
  const standardSections = [
    { key: 'participationStats', label: '참여 통계', value: props.report?.participationStats },
    { key: 'qualityEvaluation', label: '회의 품질 평가', value: props.report?.qualityEvaluation },
    { key: 'strengths', label: '잘된 점', value: props.report?.strengths },
    { key: 'improvements', label: '개선할 점', value: props.report?.improvements },
    {
      key: 'decisionProcessChecks',
      label: '의사결정 과정',
      value: props.report?.decisionProcessChecks
    },
    {
      key: 'unresolvedIssuesEvaluation',
      label: '미해결 이슈 평가',
      value: props.report?.unresolvedIssuesEvaluation
    },
    {
      key: 'nextMeetingSuggestions',
      label: '다음 회의 제안',
      value: props.report?.nextMeetingSuggestions
    }
  ]
  const additionalSections = (props.report?.additionalFeedbackSections || []).map(
    (section, index) => ({
      key: `additional-${section.key || index}`,
      label: displayKey(section.label || section.key || `추가 분석 ${index + 1}`),
      value: section.value
    })
  )
  return [...standardSections, ...additionalSections]
})

const cloneItems = (items) => (items || []).map((item) => ({ ...item }))
function resetDraft() {
  if (!props.report) return
  draft.title = props.report.title || ''
  draft.summary = props.report.summary || ''
  groups.forEach((group) => {
    draft[group.key] = cloneItems(props.report[group.key])
  })
}
watch(() => props.report, resetDraft, { immediate: true })

function addItem(group) {
  const item = { _clientId: `new-${Date.now()}-${Math.random()}` }
  group.fields.forEach((field) => {
    item[field.key] = ''
  })
  draft[group.key].push(item)
}
function removeItem(key, index) {
  draft[key].splice(index, 1)
}
function visibleFields(group, item) {
  if (!props.report?.confirmed) return group.fields
  return group.fields.filter((field) => {
    const value = item?.[field.key]
    return value !== undefined && value !== null && String(value).trim() !== ''
  })
}
function save() {
  emit('save', {
    ...draft,
    ...Object.fromEntries(groups.map((group) => [group.key, cloneItems(draft[group.key])]))
  })
}
function formatTime(seconds) {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const secs = total % 60
  return [hours, minutes, secs].map((value) => String(value).padStart(2, '0')).join(':')
}
function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function displayKey(key) {
  const labels = {
    participantId: '참가자 번호',
    speakingRatio: '발화 비중',
    participantName: '참가자 이름',
    speakingSeconds: '발화 시간',
    participantCount: '전체 참여자 수',
    activeParticipants: '적극 참여자 수',
    balanceScore: '발언 균형 점수',
    score: '종합 점수',
    clarity: '논의 명확성',
    efficiency: '진행 효율성',
    participation: '참여도',
    item: '아이템',
    comment: '코멘트',
    issue: '이슈',
    evaluation: '평가',
    ratio: '발화 비중',
    speaker: '참여자',
    utterance_count: '발화 횟수',
    speaking_seconds: '발화 시간',
    agenda_clarity: '안건 명확성',
    time_management: '시간 관리',
    decision_process: '의사결정 과정',
    discussion_focus: '논의 집중도',
    speaking_opportunity_balance: '발언 기회 균형',
    grade: '평가',
    content: '내용',
    timestamp: '시각',
    suggestion: '개선 제안',
    decision: '결정 내용',
    consensus_type: '합의 방식',
    label: '평가 항목',
    passed: '평가 결과'
  }
  return (
    labels[key] ||
    String(key)
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replaceAll('_', ' ')
  )
}
function participantValue(participant, field) {
  const aliases = {
    speaker: ['speaker', 'participantName', 'participant_name', 'participantId', 'participant_id'],
    ratio: ['ratio', 'speakingRatio', 'speaking_ratio'],
    utteranceCount: ['utterance_count', 'utteranceCount'],
    speakingSeconds: ['speaking_seconds', 'speakingSeconds']
  }
  const key = aliases[field]?.find((candidate) => participant?.[candidate] !== undefined)
  return key ? participant[key] : '-'
}
function formatSpeakingRatio(value) {
  const ratio = Number(value)
  if (!Number.isFinite(ratio)) return '-'
  const percentage = ratio <= 1 ? ratio * 100 : ratio
  return `${Number.isInteger(percentage) ? percentage : percentage.toFixed(1)}%`
}
function formatSpeakingSeconds(value) {
  const seconds = Number(value)
  if (!Number.isFinite(seconds)) return '-'
  return `${seconds.toLocaleString('ko-KR', { maximumFractionDigits: 1 })}초`
}
function labeledFields(sectionKey, item) {
  const fieldOrders = {
    strengths: ['content', 'timestamp'],
    improvements: ['issue', 'suggestion', 'timestamp'],
    decisionProcessChecks: [
      'item',
      'decision',
      'evaluation',
      'consensus_type',
      'comment',
      'timestamp'
    ],
    unresolvedIssuesEvaluation: ['issue', 'evaluation']
  }
  return (fieldOrders[sectionKey] || [])
    .filter((key) => item[key] !== undefined && item[key] !== null && item[key] !== '')
    .map((key) => ({ key, label: displayKey(key), value: item[key] }))
}
function qualityEntries(value) {
  return Object.entries(value || {}).map(([key, evaluation]) => ({
    key,
    label: displayKey(key),
    grade: isPlainObject(evaluation) ? displayValue(evaluation.grade) : displayValue(evaluation)
  }))
}
function displayValue(value) {
  if (value === null || value === undefined || value === '') return '-'
  if (typeof value === 'boolean') return value ? '충족' : '개선 필요'
  if (Array.isArray(value)) return value.map(displayValue).join(', ')
  if (isPlainObject(value)) {
    return Object.entries(value)
      .map(([key, item]) => `${displayKey(key)}: ${displayValue(item)}`)
      .join(' · ')
  }
  const labels = {
    GOOD: '좋음',
    NORMAL: '보통',
    NEEDS_IMPROVEMENT: '개선 필요',
    EXCELLENT: '매우 좋음',
    WEEKLY: '주간 회의',
    TECHNICAL: '기술 회의',
    REVIEW: '검토 회의',
    RETROSPECTIVE: '회고 회의'
  }
  return labels[value] || String(value)
}
function displayMeetingType(value) {
  if (
    value === null ||
    value === undefined ||
    value === '' ||
    (Array.isArray(value) && value.length === 0)
  )
    return ''
  const translated = displayValue(value)
  return translated === String(value) ? value : translated
}
</script>

<style scoped>
.report-modal-backdrop {
  position: fixed;
  inset: 0;
  z-index: 1200;
  display: grid;
  place-items: center;
  padding: 28px;
  background: rgba(15, 23, 42, 0.48);
  backdrop-filter: blur(5px);
}
.report-modal {
  display: flex;
  flex-direction: column;
  width: min(1040px, 94vw);
  max-height: 92vh;
  overflow: hidden;
  border: 1px solid #e2e6f0;
  border-radius: 26px;
  background: #f8f9fd;
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.22);
}
.report-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  padding: 28px 32px 22px;
  border-bottom: 1px solid #e4e7ef;
  background: #fff;
}
.report-modal-header span {
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 0.16em;
  color: #6673e8;
}
.report-modal-header h2 {
  margin: 6px 0;
  font-size: 28px;
  color: #182033;
}
.report-modal-header p {
  margin: 0;
  color: #7d8598;
}
.report-modal-header > button {
  border: 0;
  background: transparent;
  font-size: 32px;
  color: #333b4e;
  cursor: pointer;
}
.report-content {
  overflow: auto;
  padding: 26px 32px 32px;
}
.report-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
.report-toolbar strong {
  font-size: 20px;
}
.report-toolbar div {
  display: flex;
  gap: 8px;
}
.report-toolbar button,
.minutes-group header button {
  padding: 10px 15px;
  border: 1px solid #d7dce8;
  border-radius: 10px;
  background: #fff;
  color: #394158;
  font-weight: 700;
  cursor: pointer;
}
.transcript-list {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 0;
  list-style: none;
}
.transcript-list li {
  display: flex;
  gap: 14px;
  padding: 18px;
  border: 1px solid #e3e7ef;
  border-radius: 16px;
  background: #fff;
}
.speaker-avatar {
  display: grid;
  place-items: center;
  flex: 0 0 40px;
  height: 40px;
  border-radius: 13px;
  background: #e9ebff;
  color: #5967d9;
  font-weight: 800;
}
.transcript-list article {
  min-width: 0;
  flex: 1;
}
.transcript-list article > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.transcript-list time {
  font-size: 12px;
  color: #949bac;
}
.transcript-list p {
  margin: 8px 0 0;
  line-height: 1.7;
  color: #30384c;
  white-space: pre-wrap;
}
.minutes-state {
  padding: 14px 18px;
  margin-bottom: 20px;
  border-radius: 14px;
  background: #fff7e7;
  color: #805914;
}
.minutes-state.confirmed {
  background: #eaf7ef;
  color: #34724b;
}
.minutes-state span {
  font-weight: 800;
}
.minutes-state p {
  display: inline;
  margin-left: 10px;
  font-size: 13px;
}
.minutes-field {
  display: block;
  margin-bottom: 18px;
}
.minutes-field > span {
  display: block;
  margin-bottom: 8px;
  font-weight: 800;
}
.minutes-field input,
.minutes-field textarea,
.minutes-card input {
  box-sizing: border-box;
  width: 100%;
  border: 1px solid #dce1eb;
  border-radius: 12px;
  background: #fff;
  color: #20283b;
  font: inherit;
}
.minutes-field input {
  height: 48px;
  padding: 0 15px;
}
.minutes-field textarea {
  padding: 14px 15px;
  resize: vertical;
  line-height: 1.6;
}
.minutes-field input[readonly],
.minutes-field textarea[readonly],
.minutes-card input[readonly] {
  background: #f1f3f7;
  color: #5f6779;
}
.minutes-group {
  margin-top: 24px;
}
.minutes-group > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}
.minutes-group > header div {
  display: flex;
  align-items: center;
  gap: 9px;
}
.minutes-group h3 {
  margin: 0;
  font-size: 18px;
}
.minutes-group header span {
  padding: 3px 8px;
  border-radius: 20px;
  background: #e9ebf6;
  color: #667085;
  font-size: 12px;
}
.minutes-card-list {
  display: grid;
  gap: 10px;
}
.minutes-card {
  display: grid;
  grid-template-columns: 30px 1fr 30px;
  gap: 12px;
  align-items: center;
  padding: 14px 16px;
  border: 1px solid #e1e5ed;
  border-radius: 15px;
  background: #fff;
}
.minutes-card > span {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 9px;
  background: #eef0ff;
  color: #5967d9;
  font-weight: 800;
}
.minutes-card-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(140px, 1fr));
  gap: 10px;
}
.minutes-card-fields .full-width {
  grid-column: 1 / -1;
}
.minutes-card-fields small {
  display: block;
  margin-bottom: 5px;
  color: #858da0;
}
.minutes-card input,
.minutes-card textarea {
  width: 100%;
  border: 1px solid #dfe3ec;
  border-radius: 10px;
  background: #fff;
  color: #1d2438;
  font: inherit;
}
.minutes-card input {
  height: 40px;
  padding: 0 11px;
}
.minutes-card textarea {
  min-height: 68px;
  padding: 10px 11px;
  line-height: 1.6;
  resize: vertical;
}
.minutes-card input[readonly],
.minutes-card textarea[readonly] {
  border-color: transparent;
  background: #f7f8fb;
}
@media (max-width: 900px) {
  .minutes-card-fields {
    grid-template-columns: 1fr;
  }
}
.minutes-card > button {
  border: 0;
  background: transparent;
  font-size: 24px;
  color: #a0a6b3;
  cursor: pointer;
}
.report-empty {
  padding: 28px;
  text-align: center;
  color: #9299a9;
}
.minutes-content > footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 28px;
  padding-top: 20px;
  border-top: 1px solid #e1e5ed;
}
.minutes-content > footer button {
  min-width: 130px;
  padding: 12px 18px;
  border-radius: 11px;
  font-weight: 800;
  cursor: pointer;
}
.outline-btn {
  border: 1px solid #d5dae5;
  background: #fff;
}
.primary {
  border: 0;
  background: #6673e8;
  color: #fff;
}
.primary:disabled {
  opacity: 0.6;
  cursor: wait;
}
.feedback-content > .report-toolbar {
  align-items: flex-start;
}
.feedback-content > .report-toolbar > div:first-child {
  display: flex;
  min-width: 0;
  max-width: 680px;
  flex: 1;
  flex-direction: column;
  align-items: flex-start;
}
.feedback-content > .report-toolbar strong {
  max-width: 100%;
  overflow-wrap: anywhere;
  word-break: keep-all;
}
.feedback-content > .report-toolbar p {
  margin: 8px 0 0;
  color: #667085;
  line-height: 1.7;
}
.feedback-overview {
  margin-bottom: 16px;
  padding: 20px;
  border: 1px solid #dfe4ee;
  border-radius: 16px;
  background: #fff;
}
.feedback-overview h3,
.feedback-card h3 {
  margin: 0 0 10px;
  font-size: 17px;
}
.feedback-overview p,
.feedback-card p {
  margin: 0;
  color: #4d566b;
  line-height: 1.7;
}
.feedback-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}
.feedback-card {
  min-height: 140px;
  padding: 20px;
  border: 1px solid #e0e5ee;
  border-radius: 16px;
  background: #fff;
}
.feedback-card ul {
  display: grid;
  gap: 9px;
  margin: 0;
  padding-left: 19px;
  color: #4d566b;
  line-height: 1.6;
}
.feedback-card dl {
  display: grid;
  gap: 9px;
  margin: 0;
}
.feedback-card dl > div {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf0f5;
}
.feedback-card dt {
  color: #7d8598;
}
.feedback-card dd {
  margin: 0;
  font-weight: 700;
  text-align: right;
}
.participant-stat-list {
  display: grid;
  gap: 10px;
}
.participant-stat-list > article {
  padding: 14px;
  border: 1px solid #edf0f5;
  border-radius: 13px;
  background: #f8f9fc;
}
.participant-stat-list dl {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 18px;
}
.participant-stat-list dl > div {
  display: block;
  padding: 0;
  border: 0;
}
.participant-stat-list dt {
  margin-bottom: 3px;
  font-size: 12px;
}
.participant-stat-list dd {
  text-align: left;
}
.feedback-item-fields {
  display: grid;
  gap: 8px;
  margin: 0;
}
.feedback-item-fields > div {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
}
.feedback-item-fields dt {
  color: #7d8598;
  font-weight: 700;
}
.feedback-item-fields dd {
  margin: 0;
  color: #4d566b;
}
.feedback-card-decisionProcessChecks li,
.feedback-card-unresolvedIssuesEvaluation li,
.feedback-card-strengths li,
.feedback-card-improvements li {
  padding: 12px 14px;
  border-radius: 12px;
  background: #f8f9fc;
}
.quality-evaluation-list {
  display: grid;
  gap: 0;
}
.quality-evaluation-list > div {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  padding: 12px 0;
  border-bottom: 1px solid #edf0f5;
}
.quality-evaluation-list > div:last-child {
  border-bottom: 0;
}
.quality-evaluation-list dt {
  color: #596174;
  font-weight: 700;
}
.quality-evaluation-list dd {
  margin: 0;
  color: #20283b;
  font-weight: 800;
}
@media (max-width: 700px) {
  .report-modal-backdrop {
    padding: 0;
  }
  .report-modal {
    width: 100%;
    height: 100%;
    max-height: none;
    border-radius: 0;
  }
  .report-modal-header,
  .report-content {
    padding-left: 20px;
    padding-right: 20px;
  }
  .minutes-state p {
    display: block;
    margin: 5px 0 0;
  }
  .minutes-card {
    grid-template-columns: 28px 1fr;
  }
  .minutes-card > button {
    grid-column: 2;
    justify-self: end;
  }
}
@media (max-width: 700px) {
  .feedback-grid {
    grid-template-columns: 1fr;
  }
  .feedback-content > .report-toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .participant-stat-list dl {
    grid-template-columns: 1fr;
  }
}
</style>
