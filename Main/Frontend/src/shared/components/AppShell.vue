<template>
  <main class="app-shell">
    <AppTopbar :user="user" />
    <div class="workspace" :class="{ 'settings-shell': settings }">
      <slot name="navigation"></slot>
      <section :class="settings ? 'settings-content' : 'main-area'">
        <slot></slot>
      </section>
    </div>
  </main>
</template>

<script setup>
import AppTopbar from './AppTopbar.vue'

defineProps({
  user: { type: Object, default: () => ({}) },
  settings: Boolean
})
</script>
