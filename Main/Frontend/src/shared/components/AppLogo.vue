<template>
  <RouterLink class="logo" :to="to" aria-label="코밋툴 홈">
    <span class="logo-mark"></span>
  </RouterLink>
</template>

<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  to: { type: String, default: '/' }
})
</script>
