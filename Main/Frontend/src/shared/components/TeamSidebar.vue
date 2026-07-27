<template>
  <aside class="side-nav">
    <small class="eyebrow">{{ team.eyebrow }}</small>
    <h2>{{ team.name }}</h2>
    <p>{{ team.role }} · 멤버 {{ team.memberCount }}명</p>
    <nav>
      <button
        v-for="[section, icon, label] in menu"
        :key="section"
        type="button"
        :class="{ active: activeSection === section }"
        @click="$emit('navigate', section)"
      >
        <img
          class="side-icon"
          :class="`side-icon-${icon}`"
          :src="`/assets/icons/${icon}.svg`"
          alt=""
          aria-hidden="true"
        />
        <span>{{ label }}</span>
        <b v-if="section === 'members'">{{ team.memberCount }}</b>
      </button>
    </nav>
    <button type="button" class="back-main" @click="$emit('home')">← 메인으로</button>
  </aside>
</template>

<script setup>
defineProps({
  team: { type: Object, default: () => ({}) },
  menu: { type: Array, default: () => [] },
  activeSection: { type: String, default: 'schedule' }
})

defineEmits(['navigate', 'home'])
</script>
