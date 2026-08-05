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
          :src="iconPath(icon)"
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

const iconPath = icon =>
  `/assets/icons/${icon}.svg${icon === 'settings' ? '?v=20260728-1301' : ''}`
</script>

<style scoped>
.side-nav nav button {
  position: relative;
  overflow: hidden;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    box-shadow 0.2s ease,
    transform 0.2s ease;
}

.side-nav nav button::before {
  position: absolute;
  top: 50%;
  left: 0;
  width: 3px;
  height: 0;
  border-radius: 0 999px 999px 0;
  background: var(--blue);
  content: '';
  transform: translateY(-50%);
  transition: height 0.2s ease;
}

.side-nav nav button:hover:not(.active) {
  background: #f4f6ff;
  color: #344264;
  box-shadow: 0 5px 14px #31467b14;
  transform: translateX(3px);
}

.side-nav nav button:hover::before,
.side-nav nav button:focus-visible::before {
  height: 22px;
}

.side-nav nav button.active:hover {
  background: #e7eaff;
  box-shadow: 0 7px 16px #536be629;
  transform: translateY(-1px);
}

.side-nav nav button:active {
  box-shadow: none;
  transform: scale(0.985);
}

.side-nav nav button:focus-visible {
  outline: 2px solid #8797f3;
  outline-offset: 2px;
}

.side-icon {
  transition:
    filter 0.2s ease,
    transform 0.2s ease;
}

.side-nav nav button:hover .side-icon {
  transform: translateX(2px) scale(1.07);
}

.side-nav nav button b {
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.side-nav nav button:hover b {
  background: #dfe4ff;
  color: var(--blue2);
  transform: scale(1.06);
}

.back-main {
  border-radius: 8px;
  padding: 6px 9px;
  transition:
    background-color 0.2s ease,
    color 0.2s ease,
    transform 0.2s ease;
}

.back-main:hover {
  background: #f0f2fb;
  color: var(--blue2);
  transform: translateX(-3px);
}

@media (prefers-reduced-motion: reduce) {
  .side-nav nav button,
  .side-nav nav button::before,
  .side-icon,
  .side-nav nav button b,
  .back-main {
    transition: none;
  }
}
</style>
