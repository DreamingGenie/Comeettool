<template>
  <aside class="team-dock">
    <strong>TEAM</strong>
    <nav>
      <button
        v-for="workspace in workspaces"
        :key="workspace.id"
        type="button"
        class="team-bubble"
        :class="{ active: workspace.id === activeTeamId }"
        :style="workspace.color ? { backgroundColor: workspace.color } : undefined"
        :aria-label="workspace.name"
        @click="$emit('select', workspace.id)"
      >
        {{ workspace.badge }}
      </button>
      <button type="button" class="team-bubble add" @click="$emit('create')">＋</button>
    </nav>
    <div class="dock-bottom">
      <button type="button" aria-label="도움말" @click="$emit('help')">?</button>
      <button type="button" aria-label="프로필 설정" @click="$emit('profile')">
        <img src="/assets/icons/settings.svg?v=20260728-1301" alt="" aria-hidden="true" />
      </button>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  workspaces: { type: Array, default: () => [] },
  activeTeamId: { type: String, default: '' }
})

defineEmits(['select', 'create', 'profile', 'help'])
</script>

<style scoped>
.dock-bottom button {
  display: grid;
  place-items: center;
  width: 100%;
  min-height: 34px;
  border-radius: 10px;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease,
    box-shadow 0.2s ease;
}

.dock-bottom button:hover {
  background: #30405f;
  color: #fff;
  transform: translateY(-2px);
  box-shadow: 0 6px 14px #101a3040;
}

.dock-bottom button:active {
  transform: translateY(0) scale(0.96);
}

.dock-bottom img {
  display: block;
  width: 19px;
  height: 19px;
  margin: auto;
  filter: brightness(0) saturate(100%) invert(75%);
  transition: transform 0.22s ease, filter 0.22s ease;
}

.dock-bottom button:hover img {
  filter: brightness(0) saturate(100%) invert(100%);
  transform: rotate(30deg);
}
</style>
