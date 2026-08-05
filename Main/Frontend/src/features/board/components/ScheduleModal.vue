<template>
  <BaseModal modal-class="schedule-modal" @close="$emit('close')">
    <form class="schedule-form" @submit.prevent="submit">
      <small class="schedule-eyebrow">{{ isEditing ? 'EDIT SCHEDULE' : 'NEW SCHEDULE' }}</small>
      <h2>{{ isEditing ? '일정 수정하기' : '일정 추가하기' }}</h2>

      <div class="schedule-grid">
        <label class="field schedule-title-field">
          일정 제목
          <input v-model.trim="form.title" maxlength="1000" required placeholder="일정 제목을 입력하세요" />
        </label>
        <label class="field">
          카테고리
          <select v-model="form.category">
            <option value="meeting">회의</option>
            <option value="task">업무</option>
            <option value="review">리뷰</option>
            <option value="etc">기타</option>
          </select>
        </label>
        <label class="field">
          시작
          <input v-model="form.startTime" type="datetime-local" required />
        </label>
        <label class="field">
          종료
          <input v-model="form.endTime" type="datetime-local" required />
        </label>
        <label class="field schedule-wide-field">
          설명
          <textarea v-model.trim="form.description" placeholder="팀원들에게 공유할 내용을 입력하세요"></textarea>
        </label>
        <fieldset class="schedule-color-field schedule-wide-field">
          <legend>일정 색상</legend>
          <div class="schedule-colors">
            <label v-for="option in colorOptions" :key="option.value" class="schedule-color-option">
              <input v-model="form.color" type="radio" name="schedule-color" :value="option.value" />
              <span class="color-swatch" :style="{ backgroundColor: option.value }"></span>
              <small>{{ option.label }}</small>
            </label>
            <label class="schedule-color-option custom-color-option" :class="{ selected: isCustomColor }">
              <input v-model="form.color" type="color" aria-label="일정 색상 직접 지정" />
              <span class="color-swatch custom-swatch" :style="{ backgroundColor: form.color }">
                <b>＋</b>
              </span>
              <small>직접 지정</small>
            </label>
          </div>
        </fieldset>
      </div>

      <p v-if="formError" class="schedule-error" role="alert">{{ formError }}</p>

      <div v-if="isEditing && deleteConfirm" class="delete-confirm" role="alert">
        <span>이 일정을 삭제할까요?</span>
        <button type="button" :disabled="pending" @click="deleteConfirm = false">취소</button>
        <button class="danger-text" type="button" :disabled="pending" @click="$emit('delete', props.event)">삭제</button>
      </div>

      <footer class="schedule-actions">
        <button
          v-if="isEditing && !deleteConfirm"
          class="delete-button"
          type="button"
          :disabled="pending"
          @click="deleteConfirm = true"
        >
          일정 삭제
        </button>
        <span></span>
        <button class="secondary-button" type="button" :disabled="pending" @click="$emit('close')">취소</button>
        <button class="primary" type="submit" :disabled="pending">
          {{ pending ? '저장 중...' : isEditing ? '변경사항 저장' : '일정 추가하기' }}
        </button>
      </footer>
    </form>
  </BaseModal>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue'
import BaseModal from '../../../shared/components/BaseModal.vue'

const props = defineProps({
  event: { type: Object, default: null },
  year: { type: Number, required: true },
  month: { type: Number, required: true },
  day: { type: Number, required: true },
  pending: { type: Boolean, default: false }
})

const emit = defineEmits(['close', 'save', 'delete'])
const isEditing = computed(() => Boolean(props.event))
const deleteConfirm = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  category: 'meeting',
  description: '',
  startTime: '',
  endTime: '',
  color: '#7086E8'
})

const colorOptions = [
  { label: '블루', value: '#7086E8' },
  { label: '퍼플', value: '#845BEA' },
  { label: '그린', value: '#63B58D' },
  { label: '오렌지', value: '#E98032' },
  { label: '로즈', value: '#D5677C' }
]
const isCustomColor = computed(
  () => !colorOptions.some(option => option.value.toLowerCase() === form.color.toLowerCase())
)

const pad = value => String(value).padStart(2, '0')
const toLocalInput = value => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).slice(0, 16)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const defaultStart = () => `${props.year}-${pad(props.month)}-${pad(props.day)}T09:00`

watch(
  () => [props.event, props.year, props.month, props.day],
  () => {
    const startTime = toLocalInput(props.event?.startTime) || defaultStart()
    const endDate = new Date(startTime)
    endDate.setHours(endDate.getHours() + 1)
    Object.assign(form, {
      title: props.event?.title || '',
      category: props.event?.category || 'meeting',
      description: props.event?.description || '',
      startTime,
      endTime: toLocalInput(props.event?.endTime) || toLocalInput(endDate),
      color: /^#[0-9a-f]{6}$/i.test(props.event?.color || '') ? props.event.color : '#7086E8'
    })
    formError.value = ''
    deleteConfirm.value = false
  },
  { immediate: true }
)

function submit() {
  if (new Date(form.endTime) <= new Date(form.startTime)) {
    formError.value = '종료 시간은 시작 시간보다 늦어야 합니다.'
    return
  }
  emit('save', {
    ...props.event,
    ...form,
    day: Number(form.startTime.slice(8, 10)),
    userIdArr: props.event?.userIdArr || []
  })
}
</script>

<style scoped>
:global(.schedule-modal) {
  width: min(640px, calc(100vw - 32px));
  padding: 48px 34px 30px;
}

.schedule-eyebrow {
  color: #6178e2;
  font-weight: 700;
  letter-spacing: 0.12em;
}

.schedule-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 16px;
}

.schedule-title-field,
.schedule-wide-field {
  grid-column: 1 / -1;
}

.schedule-form textarea {
  resize: vertical;
}

.schedule-color-field {
  min-width: 0;
  margin: 4px 0 18px;
  padding: 0;
  border: 0;
}

.schedule-color-field legend {
  margin-bottom: 11px;
  color: #27324a;
  font-size: 13px;
  font-weight: 700;
}

.schedule-colors {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
}

.schedule-color-option {
  position: relative;
  display: grid;
  gap: 6px;
  justify-items: center;
  cursor: pointer;
}

.schedule-color-option input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  opacity: 0;
}

.color-swatch {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 4px solid #fff;
  border-radius: 50%;
  box-shadow: 0 0 0 1px #cfd6e7;
  transition: transform 0.18s ease, box-shadow 0.18s ease;
}

.schedule-color-option input:checked + .color-swatch,
.custom-color-option.selected .color-swatch {
  box-shadow: 0 0 0 3px #27324a;
  transform: scale(1.08);
}

.schedule-color-option input:focus-visible + .color-swatch {
  outline: 2px solid #7086e8;
  outline-offset: 4px;
}

.custom-swatch b {
  color: #fff;
  font-size: 18px;
  line-height: 1;
  text-shadow: 0 1px 3px #0008;
}

.schedule-color-option small {
  color: #778198;
  font-size: 10px;
  white-space: nowrap;
}

.schedule-error {
  margin: -4px 0 14px;
  color: #c43f3f;
  font-size: 12px;
}

.schedule-actions {
  display: grid;
  grid-template-columns: auto 1fr auto auto;
  gap: 10px;
  align-items: center;
  margin-top: 8px;
}

.schedule-actions button,
.delete-confirm button {
  min-height: 40px;
  padding: 0 16px;
  border-radius: 9px;
  cursor: pointer;
  font-weight: 700;
}

.secondary-button,
.delete-button,
.delete-confirm button {
  border: 1px solid #d8deed;
  background: #fff;
  color: #44506a;
}

.delete-button,
.danger-text {
  color: #c94d4d !important;
}

.delete-confirm {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
  padding: 12px 14px;
  border-radius: 10px;
  background: #fff5f5;
  color: #8d3535;
  font-size: 12px;
}

@media (max-width: 640px) {
  .schedule-grid {
    grid-template-columns: 1fr;
  }

  .schedule-title-field,
  .schedule-wide-field {
    grid-column: auto;
  }

  .schedule-actions {
    grid-template-columns: 1fr 1fr;
  }

  .schedule-actions span {
    display: none;
  }
}
</style>
