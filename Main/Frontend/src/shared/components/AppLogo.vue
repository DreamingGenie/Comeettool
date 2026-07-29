<template>
  <RouterLink class="logo" :to="to" aria-label="코밋툴 홈">
    <img
      class="logo-mark"
      :src="variant === 'blue' ? '/assets/comeetblue.svg' : '/assets/comeetwhite.svg?v=20260728-1254'"
      alt=""
      aria-hidden="true"
    />
  </RouterLink>
</template>

<script setup>
import { RouterLink } from 'vue-router'

defineProps({
  to: { type: String, default: '/' },
  variant: {
    type: String,
    default: 'white',
    validator: value => ['white', 'blue'].includes(value)
  }
})
</script>

<style scoped>
.logo.logo .logo-mark {
  display: block;
  object-fit: contain;
  background: none !important;
}
</style>
