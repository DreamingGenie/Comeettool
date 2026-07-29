<template>
  <div class="profile-color-picker">
    <div class="profile-color-picker__options" role="list" :aria-label="`${subject} 색상 목록`">
      <button
        v-for="option in displayOptions"
        :key="option.value"
        type="button"
        class="profile-color-picker__swatch"
        :class="{ active: normalizedValue === option.value }"
        :style="{ '--profile-color': option.value }"
        :aria-label="`${option.label} 색상 선택`"
        :aria-pressed="normalizedValue === option.value"
        @click="selectColor(option.value)"
      >
        <span>{{ normalizedValue === option.value ? '✓' : '' }}</span>
        <small>{{ option.label }}</small>
      </button>

      <button
        type="button"
        class="profile-color-picker__custom"
        :class="{ active: isCustomColor }"
        aria-haspopup="dialog"
        :aria-expanded="pickerOpen"
        :aria-pressed="isCustomColor"
        @click="openPicker"
      >
        <span :style="{ '--profile-color': normalizedValue }">＋</span>
        <small>직접 지정</small>
      </button>
    </div>

    <Teleport to="body">
      <button
        v-if="pickerOpen"
        type="button"
        class="profile-color-picker__backdrop"
        tabindex="-1"
        aria-label="색상 선택 닫기"
        @click="closePicker"
      ></button>

      <section
        v-if="pickerOpen"
        class="profile-color-picker__panel"
        role="dialog"
        aria-modal="true"
        :aria-label="`${subject} 사용자 지정 색상 선택`"
        @keydown.esc.stop="closePicker"
      >
        <header>
          <div>
            <b>사용자 지정</b>
            <small>원하는 색상을 직접 선택해 주세요.</small>
          </div>
          <button type="button" aria-label="색상 선택 닫기" @click="closePicker">×</button>
        </header>

        <label
          class="profile-color-picker__spectrum"
          :style="{ '--custom-color': draftColor }"
        >
          <input
            v-model="draftColor"
            type="color"
            aria-label="사용자 지정 색상"
            @input="applyDraftColor"
          />
          <span aria-hidden="true"></span>
          <small>영역을 눌러 색상 선택</small>
        </label>

        <div class="profile-color-picker__hex">
          <label for="profile-custom-color">HEX</label>
          <div :class="{ invalid: hexError }">
            <span>#</span>
            <input
              id="profile-custom-color"
              v-model.trim="hexInput"
              maxlength="6"
              autocomplete="off"
              spellcheck="false"
              aria-describedby="profile-custom-color-error"
              @input="sanitizeHex"
              @keydown.enter.prevent="applyHexColor"
            />
            <button type="button" @click="applyHexColor">적용</button>
          </div>
          <small id="profile-custom-color-error" :class="{ error: hexError }">
            {{ hexError || '6자리 HEX 값을 입력해 주세요.' }}
          </small>
        </div>

        <div class="profile-color-picker__library">
          <b>색상 팔레트</b>
          <div>
            <button
              v-for="option in paletteOptions"
              :key="`panel-${option.value}`"
              type="button"
              :style="{ '--profile-color': option.value }"
              :aria-label="`${option.label} 색상 선택`"
              :aria-pressed="normalizedValue === option.value"
              @click="selectColor(option.value)"
            >
              <span v-if="normalizedValue === option.value">✓</span>
            </button>
          </div>
        </div>
      </section>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const DEFAULT_COLOR = '#496FBD'
const DEFAULT_VISIBLE_COLORS = ['#496FBD', '#8B5CF6', '#E85D75', '#2BAF83']

const props = defineProps({
  modelValue: {
    type: String,
    default: '#496FBD'
  },
  options: {
    type: Array,
    default: () => []
  },
  subject: {
    type: String,
    default: '사용자'
  }
})

const emit = defineEmits(['update:modelValue'])

const pickerOpen = ref(false)
const draftColor = ref(normalizeHex(props.modelValue) || DEFAULT_COLOR)
const hexInput = ref(draftColor.value.slice(1))
const hexError = ref('')

const normalizedOptions = computed(() => {
  const seen = new Set()
  return props.options.reduce((result, option) => {
    const value = normalizeHex(option?.value)
    if (!value || seen.has(value)) return result
    seen.add(value)
    result.push({
      value,
      label: option?.label || value
    })
    return result
  }, [])
})

const normalizedValue = computed(
  () => normalizeHex(props.modelValue) || normalizedOptions.value[0]?.value || DEFAULT_COLOR
)

const displayOptions = computed(() => {
  const preferred = DEFAULT_VISIBLE_COLORS
    .map(color => normalizedOptions.value.find(option => option.value === color))
    .filter(Boolean)

  if (preferred.length >= 4) return preferred.slice(0, 4)

  const fallback = normalizedOptions.value.filter(
    option => !preferred.some(item => item.value === option.value)
  )
  return [...preferred, ...fallback].slice(0, 4)
})

const paletteOptions = computed(() => normalizedOptions.value.slice(0, 10))

const isCustomColor = computed(
  () => !displayOptions.value.some(option => option.value === normalizedValue.value)
)

watch(
  () => props.modelValue,
  value => {
    const normalized = normalizeHex(value)
    if (!normalized) return
    draftColor.value = normalized
    hexInput.value = normalized.slice(1)
    hexError.value = ''
  }
)

function normalizeHex(value) {
  if (typeof value !== 'string') return ''
  const candidate = value.trim().replace(/^#/, '')
  if (!/^[\da-fA-F]{6}$/.test(candidate)) return ''
  return `#${candidate.toUpperCase()}`
}

function selectColor(value) {
  const normalized = normalizeHex(value)
  if (!normalized) return
  draftColor.value = normalized
  hexInput.value = normalized.slice(1)
  hexError.value = ''
  emit('update:modelValue', normalized)
}

function sanitizeHex(event) {
  hexInput.value = event.target.value.replace(/[^\da-fA-F]/g, '').toUpperCase()
  hexError.value = ''
}

function applyDraftColor() {
  selectColor(draftColor.value)
}

function applyHexColor() {
  const normalized = normalizeHex(hexInput.value)
  if (!normalized) {
    hexError.value = '올바른 6자리 HEX 값을 입력해 주세요.'
    return
  }
  selectColor(normalized)
}

function openPicker() {
  draftColor.value = normalizedValue.value
  hexInput.value = normalizedValue.value.slice(1)
  hexError.value = ''
  pickerOpen.value = true
}

function closePicker() {
  pickerOpen.value = false
  hexError.value = ''
}
</script>

<style scoped>
.profile-color-picker {
  position: relative;
}

.profile-color-picker button {
  font: inherit;
}

.profile-color-picker__options {
  display: grid;
  grid-template-columns: repeat(5, minmax(42px, 64px));
  gap: 12px 8px;
}

.profile-color-picker__swatch,
.profile-color-picker__custom {
  display: grid;
  justify-items: center;
  gap: 5px;
  min-width: 0;
  padding: 2px;
  border: 0;
  background: transparent;
  color: #788194;
  cursor: pointer;
}

.profile-color-picker__swatch > span,
.profile-color-picker__custom > span {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: var(--profile-color);
  color: #fff;
  font-size: 12px;
  font-weight: 800;
  box-shadow: 0 0 0 1px #cbd2df;
  transition:
    transform 0.16s ease,
    box-shadow 0.16s ease;
}

.profile-color-picker__swatch:hover > span,
.profile-color-picker__custom:hover > span {
  transform: translateY(-2px) scale(1.06);
}

.profile-color-picker__swatch:focus-visible,
.profile-color-picker__custom:focus-visible {
  outline: 2px solid #6674e8;
  outline-offset: 3px;
  border-radius: 6px;
}

.profile-color-picker__swatch > small,
.profile-color-picker__custom > small {
  width: 100%;
  overflow: hidden;
  font-size: 9px;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.profile-color-picker__swatch.active > span {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--profile-color);
}

.profile-color-picker__swatch.active > small,
.profile-color-picker__custom.active > small {
  color: #17233b;
  font-weight: 700;
}

.profile-color-picker__custom > span {
  background:
    linear-gradient(#fff, #fff) padding-box,
    conic-gradient(
        #e85d75,
        #f59e42,
        #d9e849,
        #2baf83,
        #36a6a1,
        #496fbd,
        #8b5cf6,
        #e85d75
      )
      border-box;
  border: 3px solid transparent;
  color: #4f5d75;
  font-size: 18px;
  font-weight: 500;
  box-shadow: 0 0 0 1px #cbd2df;
}

.profile-color-picker__custom.active > span {
  background: var(--profile-color);
  border-color: #fff;
  color: #fff;
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--profile-color);
}

.profile-color-picker__backdrop {
  position: fixed;
  z-index: 39;
  inset: 0;
  width: 100vw;
  height: 100vh;
  padding: 0;
  border: 0;
  background: #17233b20;
  cursor: default;
}

.profile-color-picker__panel {
  position: fixed;
  z-index: 40;
  top: 24px;
  left: 50%;
  width: min(340px, calc(100vw - 32px));
  max-height: calc(100vh - 32px);
  overflow-x: hidden;
  overflow-y: auto;
  padding: 18px;
  border: 1px solid #dfe3ec;
  border-radius: 18px;
  background: #fff;
  color: #17233b;
  box-shadow: 0 18px 50px #17233b30;
  transform: translateX(-50%);
}

.profile-color-picker__panel > header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
}

.profile-color-picker__panel > header > div {
  display: grid;
  gap: 4px;
}

.profile-color-picker__panel > header b {
  font-size: 15px;
}

.profile-color-picker__panel > header small {
  color: #8790a2;
  font-size: 10px;
}

.profile-color-picker__panel > header button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 8px;
  background: #f3f5fa;
  color: #5e687b;
  font-size: 20px;
  cursor: pointer;
}

.profile-color-picker__spectrum {
  position: relative;
  display: grid;
  place-items: end start;
  height: 170px;
  overflow: hidden;
  border: 1px solid #d9dee8;
  border-radius: 12px;
  background:
    linear-gradient(to bottom, transparent, #000),
    linear-gradient(to right, #fff, var(--custom-color));
  cursor: pointer;
}

.profile-color-picker__spectrum input {
  position: absolute;
  z-index: 2;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.profile-color-picker__spectrum > span {
  position: absolute;
  z-index: 1;
  top: 24px;
  right: 28px;
  width: 17px;
  height: 17px;
  border: 3px solid #fff;
  border-radius: 50%;
  background: var(--custom-color);
  box-shadow: 0 1px 4px #17233b80;
}

.profile-color-picker__spectrum > small {
  position: relative;
  z-index: 1;
  margin: 0 0 9px 10px;
  color: #fff;
  font-size: 9px;
  text-shadow: 0 1px 3px #000;
}

.profile-color-picker__hex {
  display: grid;
  grid-template-columns: 42px 1fr;
  align-items: center;
  gap: 6px 10px;
  margin-top: 14px;
}

.profile-color-picker__hex > label {
  font-size: 10px;
  font-weight: 800;
}

.profile-color-picker__hex > div {
  display: grid;
  grid-template-columns: 20px 1fr auto;
  align-items: center;
  height: 38px;
  overflow: hidden;
  border: 1px solid #d8deea;
  border-radius: 9px;
}

.profile-color-picker__hex > div.invalid {
  border-color: #d64050;
}

.profile-color-picker__hex span {
  padding-left: 10px;
  color: #7f899b;
}

.profile-color-picker__hex input {
  min-width: 0;
  height: 100%;
  padding: 0 6px;
  border: 0;
  outline: 0;
  color: #17233b;
  font-weight: 700;
  text-transform: uppercase;
}

.profile-color-picker__hex button {
  align-self: stretch;
  padding: 0 12px;
  border: 0;
  border-left: 1px solid #e2e5ec;
  background: #f4f6fb;
  color: #5369db;
  font-size: 10px;
  font-weight: 700;
  cursor: pointer;
}

.profile-color-picker__hex > small {
  grid-column: 2;
  color: #8b93a2;
  font-size: 9px;
}

.profile-color-picker__hex > small.error {
  color: #c72f40;
}

.profile-color-picker__library {
  display: grid;
  gap: 10px;
  margin-top: 16px;
  padding-top: 14px;
  border-top: 1px solid #e5e8ef;
}

.profile-color-picker__library > b {
  font-size: 11px;
}

.profile-color-picker__library > div {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 12px;
  padding: 4px;
}

.profile-color-picker__library button {
  display: grid;
  place-items: center;
  aspect-ratio: 1;
  padding: 0;
  border: 2px solid #fff;
  border-radius: 7px;
  background: var(--profile-color);
  color: #fff;
  font-size: 10px;
  font-weight: 800;
  box-shadow: 0 0 0 1px #d5dbe6;
  cursor: pointer;
  transition:
    transform 0.14s ease,
    box-shadow 0.14s ease;
}

.profile-color-picker__library button:hover {
  transform: scale(1.08);
}

.profile-color-picker__library button[aria-pressed='true'] {
  box-shadow:
    0 0 0 2px #fff,
    0 0 0 4px var(--profile-color);
}

</style>
