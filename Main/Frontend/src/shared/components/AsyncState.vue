<template>
  <div v-if="loading" class="async-state" role="status">불러오는 중...</div>
  <div v-else-if="error" class="async-state error" role="alert">
    {{ error }}
    <button v-if="retry" type="button" @click="retry">다시 시도</button>
  </div>
  <div v-else-if="empty" class="async-state">표시할 데이터가 없습니다.</div>
</template>

<script setup>
defineProps({
  loading: Boolean,
  error: { type: String, default: '' },
  empty: Boolean,
  retry: { type: Function, default: null }
})
</script>
