<template>
  <header class="topbar">
    <AppLogo to="/home" />
    <button class="user-pill" type="button" @click="$router.push('/profile')">
      <i>{{ user?.avatarText || '' }}</i>
      {{ user?.nickname || '' }}
    </button>
  </header>
</template>

<script setup>
import AppLogo from './AppLogo.vue'

defineProps({
  user: { type: Object, default: () => ({}) }
})
</script>
