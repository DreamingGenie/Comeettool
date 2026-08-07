<template>
  <span class="user-avatar" :style="fallbackStyle" aria-hidden="true">
    <img
      v-if="resolvedImageUrl && !imageFailed"
      :src="resolvedImageUrl"
      alt=""
      @error="imageFailed = true"
    />
    <span v-else>{{ fallbackText }}</span>
  </span>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  imageUrl: { type: String, default: '' },
  fallbackText: { type: String, default: '?' },
  fallbackColor: { type: String, default: '' }
})

const imageFailed = ref(false)
const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '').replace(/\/$/, '')
const resolvedImageUrl = computed(() => {
  const imageUrl = props.imageUrl?.trim()
  if (!imageUrl || imageUrl.startsWith('blob:') || imageUrl.startsWith('data:')) return imageUrl

  if (imageUrl.startsWith('/')) return apiBaseUrl ? `${apiBaseUrl}${imageUrl}` : imageUrl

  try {
    const parsedUrl = new URL(imageUrl)
    if (apiBaseUrl && ['localhost', '127.0.0.1'].includes(parsedUrl.hostname)) {
      return `${apiBaseUrl}${parsedUrl.pathname}${parsedUrl.search}${parsedUrl.hash}`
    }
  } catch {
    return imageUrl
  }

  return imageUrl
})
const fallbackStyle = computed(() =>
  props.fallbackColor ? { backgroundColor: props.fallbackColor } : undefined
)

watch(
  () => resolvedImageUrl.value,
  () => {
    imageFailed.value = false
  }
)
</script>

<style scoped>
.user-avatar {
  display: grid;
  overflow: hidden;
  place-items: center;
  flex: 0 0 auto;
  font-style: normal;
}

.user-avatar img {
  display: block;
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}
</style>
