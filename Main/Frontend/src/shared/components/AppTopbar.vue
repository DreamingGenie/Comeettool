<template>
  <header class="topbar">
    <AppSparkles />
    <AppLogo to="/home" />
    <button class="user-pill" type="button" @click="$router.push('/profile')">
      <i :style="user?.color ? { backgroundColor: user.color } : undefined">
        {{ user?.avatarText || '' }}
      </i>
      {{ user?.nickname || '' }}
    </button>
  </header>
</template>

<script setup>
import AppLogo from './AppLogo.vue'
import AppSparkles from './AppSparkles.vue'

defineProps({
  user: { type: Object, default: () => ({}) }
})
</script>

<style scoped>
.topbar {
  position: relative;
  overflow: hidden;
}

.topbar > :not(.app-sparkles) {
  position: relative;
  z-index: 1;
}
</style>
