<template>
  <div class="workspace-menu-layer" @pointerdown.self="$emit('close')">
    <div
      class="workspace-context-menu"
      :style="menuStyle"
      role="menu"
      :aria-label="`${workspace.name} 메뉴`"
      @click.stop
      @contextmenu.prevent
    >
      <header>
        <i :style="workspace.color ? { backgroundColor: workspace.color } : undefined">
          {{ workspace.badge }}
        </i>
        <span>
          <b>{{ workspace.name }}</b>
          <small>{{ workspace.role }} · 멤버 {{ workspace.members }}명</small>
        </span>
      </header>

      <button type="button" role="menuitem" @click="$emit('select', 'open')">
        <img src="/assets/icons/calendar.svg" alt="" aria-hidden="true" />
        팀 스페이스 열기
      </button>
      <button v-if="isOwner" type="button" role="menuitem" @click="$emit('select', 'invite')">
        <img src="/assets/icons/people.svg" alt="" aria-hidden="true" />
        멤버 초대
      </button>
      <button v-else type="button" role="menuitem" @click="$emit('select', 'members')">
        <img src="/assets/icons/members.svg" alt="" aria-hidden="true" />
        멤버 보기
      </button>
      <button v-if="isOwner" type="button" role="menuitem" @click="$emit('select', 'settings')">
        <img src="/assets/icons/settings.svg" alt="" aria-hidden="true" />
        팀 설정
      </button>

      <hr />
      <button
        class="danger"
        type="button"
        role="menuitem"
        @click="$emit('select', isOwner ? 'delete' : 'leave')"
      >
        <span aria-hidden="true">{{ isOwner ? '×' : '↗' }}</span>
        {{ isOwner ? '팀 스페이스 삭제' : '팀 스페이스 나가기' }}
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'

const props = defineProps({
  workspace: { type: Object, required: true },
  position: {
    type: Object,
    default: () => ({ x: 0, y: 0 })
  }
})

const emit = defineEmits(['close', 'select'])

const isOwner = computed(() => String(props.workspace.role || '').toLowerCase() === 'owner')
const menuStyle = computed(() => ({
  left: `${props.position.x}px`,
  top: `${props.position.y}px`
}))

const closeOnEscape = (event) => {
  if (event.key === 'Escape') emit('close')
}

onMounted(() => window.addEventListener('keydown', closeOnEscape))
onBeforeUnmount(() => window.removeEventListener('keydown', closeOnEscape))
</script>

<style scoped>
.workspace-menu-layer {
  position: fixed;
  z-index: 90;
  inset: 0;
}

.workspace-context-menu {
  position: fixed;
  display: grid;
  width: 188px;
  padding: 7px;
  border: 1px solid var(--dropdown-border);
  border-radius: var(--dropdown-panel-radius);
  background: #fff;
  color: #1b2539;
  box-shadow: var(--dropdown-shadow);
  animation: workspace-menu-in 0.16s ease-out;
}

.workspace-context-menu header {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  padding: 7px 8px 10px;
  border-bottom: 1px solid #edf0f5;
  margin-bottom: 5px;
}

.workspace-context-menu header > i {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 31px;
  height: 31px;
  border-radius: 10px;
  background: #566fea;
  color: #fff;
  font-size: 11px;
  font-style: normal;
  font-weight: 800;
}

.workspace-context-menu header > span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.workspace-context-menu header b,
.workspace-context-menu header small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.workspace-context-menu header b {
  font-size: 12px;
}

.workspace-context-menu header small {
  color: #7a8395;
  font-size: 9px;
}

.workspace-context-menu button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 34px;
  padding: 0 10px;
  border: 0;
  border-radius: 10px;
  background: transparent;
  color: inherit;
  font-size: 11px;
  font-weight: 600;
  text-align: left;
}

.workspace-context-menu button:hover,
.workspace-context-menu button:focus-visible {
  outline: 0;
  background: var(--dropdown-option-hover);
  color: #4f65dc;
}

.workspace-context-menu button img,
.workspace-context-menu button > span {
  width: 16px;
  height: 16px;
  object-fit: contain;
  opacity: 0.72;
}

.workspace-context-menu button > span {
  display: grid;
  place-items: center;
  font-size: 18px;
  line-height: 1;
}

.workspace-context-menu hr {
  width: calc(100% - 8px);
  height: 1px;
  margin: 5px 4px;
  border: 0;
  background: #edf0f5;
}

.workspace-context-menu button.danger {
  color: #c84242;
}

.workspace-context-menu button.danger:hover,
.workspace-context-menu button.danger:focus-visible {
  background: #fff0f0;
  color: #b82f2f;
}

@keyframes workspace-menu-in {
  from {
    opacity: 0;
    transform: translateY(-5px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
</style>
