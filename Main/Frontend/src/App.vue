<template>
  <div ref="legacyRoot" class="vue-app-root"></div>
  <div ref="toastRoot" class="toast" role="status" aria-live="polite"></div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { mountCommitTool } from './legacy/committool'

const legacyRoot = ref(null)
const toastRoot = ref(null)
let cleanup

onMounted(() => {
  cleanup = mountCommitTool(legacyRoot.value, toastRoot.value)
})

onBeforeUnmount(() => cleanup?.())
</script>
