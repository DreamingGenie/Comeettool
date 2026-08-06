<template>
  <aside class="team-dock">
    <strong>TEAM</strong>
    <nav>
      <button
        v-for="workspace in workspaces"
        :key="workspace.id"
        type="button"
        class="team-bubble"
        :class="{
          active: String(workspace.id) === String(activeTeamId),
          dragging: draggingWorkspaceId === String(workspace.id),
          'drop-shift-up':
            dropTargetWorkspaceId === String(workspace.id) && dragDirection === 'down',
          'drop-shift-down':
            dropTargetWorkspaceId === String(workspace.id) && dragDirection === 'up'
        }"
        :style="workspace.color ? { backgroundColor: workspace.color } : undefined"
        :aria-label="workspace.name"
        draggable="true"
        :aria-grabbed="draggingWorkspaceId === String(workspace.id)"
        @click="selectWorkspace(workspace.id)"
        @dragstart="startWorkspaceDrag($event, workspace.id)"
        @dragend="endWorkspaceDrag"
        @dragenter.prevent="previewWorkspaceDrop(workspace.id)"
        @dragover.prevent
        @drop.prevent="dropWorkspace(workspace.id)"
      >
        <img v-if="workspace.profileImage" :src="workspace.profileImage" alt="" />
        <span v-else>{{ workspace.badge }}</span>
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
import { ref } from 'vue'

const props = defineProps({
  workspaces: { type: Array, default: () => [] },
  activeTeamId: { type: String, default: '' }
})

const emit = defineEmits(['select', 'create', 'profile', 'help', 'reorder'])
const draggingWorkspaceId = ref('')
const dropTargetWorkspaceId = ref('')
const dragDirection = ref('')
let suppressWorkspaceClick = false

const selectWorkspace = workspaceId => {
  if (!suppressWorkspaceClick) emit('select', workspaceId)
}

const startWorkspaceDrag = (event, workspaceId) => {
  draggingWorkspaceId.value = String(workspaceId)
  suppressWorkspaceClick = true
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('text/plain', draggingWorkspaceId.value)
}

const endWorkspaceDrag = () => {
  draggingWorkspaceId.value = ''
  dropTargetWorkspaceId.value = ''
  dragDirection.value = ''
  setTimeout(() => {
    suppressWorkspaceClick = false
  }, 0)
}

const previewWorkspaceDrop = targetWorkspaceId => {
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) {
    dropTargetWorkspaceId.value = ''
    dragDirection.value = ''
    return
  }

  const workspaceIds = props.workspaces.map(workspace => String(workspace.id))
  const sourceIndex = workspaceIds.indexOf(sourceId)
  const targetIndex = workspaceIds.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) return

  dropTargetWorkspaceId.value = targetId
  dragDirection.value = sourceIndex < targetIndex ? 'down' : 'up'
}

const dropWorkspace = targetWorkspaceId => {
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) return

  const nextOrder = props.workspaces.map(workspace => String(workspace.id))
  const sourceIndex = nextOrder.indexOf(sourceId)
  const targetIndex = nextOrder.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) return

  nextOrder.splice(sourceIndex, 1)
  nextOrder.splice(targetIndex, 0, sourceId)
  emit('reorder', nextOrder)
  endWorkspaceDrag()
}
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

.team-bubble[draggable='true'] {
  cursor: grab;
}

.team-bubble[draggable='true']:active {
  cursor: grabbing;
}

.team-bubble.dragging {
  opacity: 0.45;
}

.team-bubble {
  display: grid;
  overflow: hidden;
  place-items: center;
  transition: transform 0.18s ease, opacity 0.18s ease,
    box-shadow 0.18s ease, background-color 0.18s ease;
}

.team-bubble > img {
  width: 100%;
  height: 100%;
  border-radius: inherit;
  object-fit: cover;
}

.team-bubble.drop-shift-up {
  transform: translateY(-7px);
  box-shadow: 0 10px 18px rgba(8, 17, 39, 0.32);
}

.team-bubble.drop-shift-down {
  transform: translateY(7px);
  box-shadow: 0 -10px 18px rgba(8, 17, 39, 0.32);
}
</style>
