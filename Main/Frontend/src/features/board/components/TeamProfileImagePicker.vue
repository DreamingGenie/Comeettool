<template>
  <div class="team-profile-picker" :class="{ compact }">
    <div
      class="team-profile-preview"
      :style="!displayImage ? { backgroundColor: color || '#5f73cf' } : undefined"
      aria-hidden="true"
    >
      <img
        v-if="displayImage"
        :src="modelValue"
        alt=""
        @error="imageFailed = true"
      />
      <span v-else>{{ fallbackText }}</span>
    </div>

    <div class="team-profile-copy">
      <b>{{ label }}</b>
      <small>JPG, PNG · 최대 5MB</small>
      <button type="button" class="team-profile-upload" @click="openFilePicker">
        {{ modelValue ? '이미지 변경' : '이미지 업로드' }}
      </button>
    </div>

    <input
      ref="fileInput"
      class="team-profile-file-input"
      type="file"
      accept="image/jpeg,image/png,.jpg,.jpeg,.png"
      @change="selectFile"
    />
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const MAX_IMAGE_SIZE = 5 * 1024 * 1024
const ALLOWED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png'])

const props = defineProps({
  modelValue: { type: String, default: '' },
  fallback: { type: String, default: 'TM' },
  color: { type: String, default: '' },
  label: { type: String, default: '팀 프로필 이미지' },
  compact: { type: Boolean, default: false }
})

const emit = defineEmits(['file-change', 'error'])
const fileInput = ref(null)
const imageFailed = ref(false)
const displayImage = computed(() => Boolean(props.modelValue) && !imageFailed.value)
const fallbackText = computed(() => String(props.fallback || 'TM').slice(0, 2).toUpperCase())

watch(
  () => props.modelValue,
  () => {
    imageFailed.value = false
  }
)

const openFilePicker = () => fileInput.value?.click()

const selectFile = event => {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return

  if (!ALLOWED_IMAGE_TYPES.has(file.type)) {
    emit('error', 'JPG 또는 PNG 이미지만 등록할 수 있습니다.')
    return
  }
  if (file.size > MAX_IMAGE_SIZE) {
    emit('error', '팀 프로필 이미지는 5MB 이하만 등록할 수 있습니다.')
    return
  }

  imageFailed.value = false
  emit('file-change', file)
}
</script>

<style scoped>
.team-profile-picker {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px;
  border: 1px solid #e1e5ed;
  border-radius: 12px;
  background: #f8f9fc;
}

.team-profile-preview {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 64px;
  height: 64px;
  overflow: hidden;
  border: 3px solid #fff;
  border-radius: 20px;
  color: #fff;
  box-shadow: 0 0 0 1px #d9deea;
  font-size: 17px;
  font-weight: 800;
}

.team-profile-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.team-profile-copy {
  display: grid;
  min-width: 0;
  gap: 4px;
}

.team-profile-copy b {
  color: #1d263a;
  font-size: 12px;
}

.team-profile-copy small {
  color: #7a8394;
  font-size: 9px;
}

.team-profile-upload {
  width: fit-content;
  margin-top: 4px;
  padding: 7px 12px;
  border: 1px solid #cfd5e5;
  border-radius: 8px;
  background: #fff;
  color: #566bea;
  font-size: 10px;
  font-weight: 700;
}

.team-profile-upload:hover,
.team-profile-upload:focus-visible {
  border-color: #7788ed;
  outline: none;
  background: #f2f4ff;
}

.team-profile-file-input {
  display: none;
}

.team-profile-picker.compact {
  margin-left: auto;
  padding: 8px 10px;
  border: 0;
  background: transparent;
}

.team-profile-picker.compact .team-profile-preview {
  width: 46px;
  height: 46px;
  border-radius: 14px;
  font-size: 13px;
}

.team-profile-picker.compact .team-profile-copy {
  gap: 2px;
}

.team-profile-picker.compact .team-profile-upload {
  margin-top: 2px;
  padding: 5px 9px;
}

@media (max-width: 620px) {
  .team-profile-picker.compact {
    width: 100%;
    margin-left: 0;
  }
}
</style>
