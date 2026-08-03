<template>
  <div ref="root" class="app-select" :class="{ 'is-open': open, 'is-disabled': disabled }">
    <button
      ref="trigger"
      class="app-select__trigger"
      type="button"
      role="combobox"
      aria-haspopup="listbox"
      :aria-expanded="open"
      :aria-controls="listboxId"
      :aria-activedescendant="open && activeIndex >= 0 ? `${listboxId}-option-${activeIndex}` : undefined"
      :aria-label="ariaLabel"
      :disabled="disabled"
      @click="toggle"
      @keydown="onTriggerKeydown"
    >
      <span :class="{ 'is-placeholder': !selectedOption }">{{ selectedLabel }}</span>
      <svg viewBox="0 0 20 20" aria-hidden="true">
        <path d="m5 7.5 5 5 5-5" />
      </svg>
    </button>

    <Teleport to="body">
      <Transition name="app-select-panel">
        <div
          v-if="open"
          :id="listboxId"
          ref="panel"
          class="app-select__panel"
          :class="{ 'opens-up': opensUp }"
          :style="panelStyle"
          role="listbox"
          tabindex="-1"
          :aria-label="ariaLabel"
          @keydown="onPanelKeydown"
        >
          <button
            v-for="(option, index) in normalizedOptions"
            :id="`${listboxId}-option-${index}`"
            :key="option.key"
            class="app-select__option"
            :class="{ 'is-selected': isSelected(option.value), 'is-active': index === activeIndex }"
            type="button"
            role="option"
            :aria-selected="isSelected(option.value)"
            :disabled="option.disabled"
            @mouseenter="setActive(index)"
            @click="select(option)"
          >
            <span>{{ option.label }}</span>
            <svg v-if="isSelected(option.value)" viewBox="0 0 20 20" aria-hidden="true">
              <path d="m4 10 4 4 8-8" />
            </svg>
          </button>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  modelValue: { default: null },
  options: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  placeholder: { type: String, default: '선택하세요' },
  ariaLabel: { type: String, default: '옵션 선택' }
})

const emit = defineEmits(['update:modelValue', 'change', 'open', 'close'])
const root = ref(null)
const trigger = ref(null)
const panel = ref(null)
const open = ref(false)
const activeIndex = ref(-1)
const opensUp = ref(false)
const panelStyle = ref({})
const listboxId = `app-select-${Math.random().toString(36).slice(2, 9)}`

const normalizedOptions = computed(() =>
  props.options.map((option, index) => {
    const normalized = option && typeof option === 'object' && !Array.isArray(option)
      ? option
      : { value: option, label: String(option ?? '') }
    return {
      value: normalized.value,
      label: String(normalized.label ?? normalized.value ?? ''),
      disabled: Boolean(normalized.disabled),
      key: normalized.key ?? `${String(normalized.value)}-${index}`
    }
  })
)

const selectedOption = computed(() =>
  normalizedOptions.value.find((option) => isSelected(option.value))
)
const selectedLabel = computed(() => selectedOption.value?.label || props.placeholder)

function isSelected(value) {
  return Object.is(value, props.modelValue)
}

function firstEnabledIndex() {
  return normalizedOptions.value.findIndex((option) => !option.disabled)
}

function selectedIndex() {
  const index = normalizedOptions.value.findIndex((option) => isSelected(option.value) && !option.disabled)
  return index >= 0 ? index : firstEnabledIndex()
}

function updatePosition() {
  if (!open.value || !trigger.value) return
  const rect = trigger.value.getBoundingClientRect()
  const gap = 8
  const maxHeight = Math.min(280, Math.max(112, normalizedOptions.value.length * 48 + 16))
  const availableBelow = window.innerHeight - rect.bottom - gap
  const availableAbove = rect.top - gap
  opensUp.value = availableBelow < Math.min(maxHeight, 180) && availableAbove > availableBelow
  const height = Math.min(maxHeight, opensUp.value ? availableAbove : availableBelow)
  panelStyle.value = {
    left: `${rect.left}px`,
    top: opensUp.value ? 'auto' : `${rect.bottom + gap}px`,
    bottom: opensUp.value ? `${window.innerHeight - rect.top + gap}px` : 'auto',
    width: `${rect.width}px`,
    maxHeight: `${Math.max(96, height)}px`
  }
}

async function show() {
  if (props.disabled || open.value) return
  activeIndex.value = selectedIndex()
  open.value = true
  emit('open')
  await nextTick()
  updatePosition()
  panel.value?.focus?.()
  panel.value?.querySelector('.is-active')?.scrollIntoView({ block: 'nearest' })
}

function hide({ focusTrigger = false } = {}) {
  if (!open.value) return
  open.value = false
  emit('close')
  if (focusTrigger) nextTick(() => trigger.value?.focus())
}

function toggle() {
  if (open.value) hide()
  else show()
}

function setActive(index) {
  if (!normalizedOptions.value[index]?.disabled) activeIndex.value = index
}

function moveActive(step) {
  const options = normalizedOptions.value
  if (!options.length) return
  let index = activeIndex.value
  for (let count = 0; count < options.length; count += 1) {
    index = (index + step + options.length) % options.length
    if (!options[index].disabled) {
      activeIndex.value = index
      nextTick(() => panel.value?.querySelector('.is-active')?.scrollIntoView({ block: 'nearest' }))
      return
    }
  }
}

function select(option) {
  if (option.disabled) return
  emit('update:modelValue', option.value)
  emit('change', option.value)
  hide({ focusTrigger: true })
}

function onTriggerKeydown(event) {
  if (open.value) {
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault()
      moveActive(event.key === 'ArrowDown' ? 1 : -1)
    } else if (event.key === 'Enter' || event.key === ' ') {
      event.preventDefault()
      const option = normalizedOptions.value[activeIndex.value]
      if (option) select(option)
    } else if (event.key === 'Escape') {
      event.preventDefault()
      hide({ focusTrigger: true })
    }
    return
  }
  if (['ArrowDown', 'ArrowUp', 'Enter', ' '].includes(event.key)) {
    event.preventDefault()
    show()
  }
}

function onPanelKeydown(event) {
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    moveActive(event.key === 'ArrowDown' ? 1 : -1)
  } else if (event.key === 'Home' || event.key === 'End') {
    event.preventDefault()
    activeIndex.value = event.key === 'Home' ? firstEnabledIndex() : normalizedOptions.value.length - 1
    if (normalizedOptions.value[activeIndex.value]?.disabled) moveActive(event.key === 'Home' ? 1 : -1)
  } else if (event.key === 'Enter' || event.key === ' ') {
    event.preventDefault()
    const option = normalizedOptions.value[activeIndex.value]
    if (option) select(option)
  } else if (event.key === 'Escape' || event.key === 'Tab') {
    hide({ focusTrigger: event.key === 'Escape' })
  }
}

function onDocumentPointerDown(event) {
  if (!open.value) return
  if (root.value?.contains(event.target) || panel.value?.contains(event.target)) return
  hide()
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  window.addEventListener('resize', updatePosition)
  window.addEventListener('scroll', updatePosition, true)
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  window.removeEventListener('resize', updatePosition)
  window.removeEventListener('scroll', updatePosition, true)
})
</script>

<style scoped>
.app-select {
  position: relative;
  width: 100%;
  min-width: 0;
}

.app-select__trigger {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 14px;
  border: 1px solid var(--dropdown-border, #d7ddec);
  border-radius: var(--dropdown-radius, 12px);
  background: var(--dropdown-surface, #fff);
  color: #202941;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: border-color 0.18s ease, box-shadow 0.18s ease, background 0.18s ease;
}

.app-select__trigger:hover {
  border-color: var(--dropdown-border-hover, #aeb9dc);
}

.app-select__trigger:focus-visible,
.is-open .app-select__trigger {
  outline: none;
  border-color: #6574e8;
  box-shadow: 0 0 0 3px rgba(101, 116, 232, 0.14);
}

.app-select__trigger span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-select__trigger .is-placeholder {
  color: #8d96aa;
}

.app-select__trigger svg {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 1.8;
  transition: transform 0.18s ease;
}

.is-open .app-select__trigger svg {
  transform: rotate(180deg);
}

.app-select__trigger:disabled {
  color: #a3aabd;
  background: #f3f5fa;
  cursor: not-allowed;
}

.app-select__panel {
  position: fixed;
  z-index: 1600;
  overflow-y: auto;
  padding: 8px;
  border: 1px solid var(--dropdown-border, #d7ddec);
  border-radius: var(--dropdown-panel-radius, 16px);
  background: var(--dropdown-surface, #fff);
  box-shadow: var(--dropdown-shadow, 0 16px 38px rgba(34, 43, 73, 0.16));
  outline: none;
  scrollbar-width: thin;
}

.app-select__option {
  display: flex;
  width: 100%;
  min-height: 42px;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 9px 12px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: #273048;
  font: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.14s ease, color 0.14s ease;
}

.app-select__option:hover,
.app-select__option.is-active {
  background: var(--dropdown-option-hover, #f1f3ff);
}

.app-select__option.is-selected {
  background: var(--dropdown-option, #eef0ff);
  color: #5968df;
  font-weight: 700;
}

.app-select__option:disabled {
  color: #a7adbb;
  cursor: not-allowed;
}

.app-select__option svg {
  width: 18px;
  height: 18px;
  flex: 0 0 auto;
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-width: 2;
}

.app-select-panel-enter-active,
.app-select-panel-leave-active {
  transition: opacity 0.14s ease, transform 0.14s ease;
  transform-origin: top;
}

.app-select-panel-enter-from,
.app-select-panel-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

.app-select__panel.opens-up {
  transform-origin: bottom;
}
</style>
