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
        :aria-label="workspace.name"
        @click="$emit('select', workspace.id)"
      >
        {{ workspace.badge }}
      </button>
      <button type="button" class="team-bubble add" @click="$emit('create')">＋</button>
    </nav>
    <div class="dock-bottom">
      <button type="button" aria-label="도움말">?</button>
      <button type="button" aria-label="프로필 설정" @click="$emit('profile')">⚙</button>
    </div>
  </aside>
</template>

<script setup>
defineProps({
  workspaces: { type: Array, default: () => [] },
  activeTeamId: { type: String, default: '' }
})

defineEmits(['select', 'create', 'profile'])
</script>
