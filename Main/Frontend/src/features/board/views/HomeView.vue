<template>
  <main class="app-shell main-home">
    <AppTopbar :user="userState.profile || {}" />
    <div class="main-home-layout">
      <aside class="workspace-sidebar">
        <label class="workspace-search">
          ⌕
          <input v-model.trim="query" placeholder="팀 스페이스 검색" />
        </label>
        <small>MY WORKSPACES</small>
        <nav>
          <article
            v-for="workspace in boardState.workspaces"
            :key="workspace.id"
            class="workspace-item"
            :class="{
              dragging: draggingWorkspaceId === String(workspace.id),
              'drop-shift-up':
                dropTargetWorkspaceId === String(workspace.id) && dragDirection === 'down',
              'drop-shift-down':
                dropTargetWorkspaceId === String(workspace.id) && dragDirection === 'up'
            }"
            role="button"
            tabindex="0"
            :draggable="!query"
            :aria-grabbed="draggingWorkspaceId === String(workspace.id)"
            @click="openWorkspace(workspace.id)"
            @keydown.enter.self="openWorkspace(workspace.id)"
            @keydown.space.self.prevent="openWorkspace(workspace.id)"
            @contextmenu="openWorkspaceMenu($event, workspace, 'context')"
            @dragstart="startWorkspaceDrag($event, workspace.id)"
            @dragend="endWorkspaceDrag"
            @dragenter.prevent="previewWorkspaceDrop(workspace.id)"
            @dragover.prevent
            @drop.prevent="dropWorkspace(workspace.id)"
          >
            <i :style="workspace.color ? { backgroundColor: workspace.color } : undefined">
              <img v-if="workspace.profileImage" :src="workspace.profileImage" alt="" />
              <span v-else>{{ workspace.badge }}</span>
            </i>
            <span>
              <b>{{ workspace.name }}</b>
              <small>{{ workspace.role }} · 멤버 {{ workspace.members }}명</small>
            </span>
            <button
              class="workspace-more"
              type="button"
              draggable="false"
              :aria-label="`${workspace.name} 메뉴 열기`"
              aria-haspopup="menu"
              :aria-expanded="String(menuWorkspace?.id) === String(workspace.id)"
              @click.stop="openWorkspaceMenu($event, workspace, 'button')"
              @keydown.enter.stop
              @keydown.space.stop
              @dragstart.stop.prevent
            >
              •••
            </button>
          </article>
          <button
            class="workspace-add"
            type="button"
            aria-label="새 팀 스페이스 만들기"
            @click="showNewTeam = true"
          >
            <span aria-hidden="true"></span>
          </button>
        </nav>
        <footer>
          <button type="button" @click="showHelp = true">ⓘ　도움말 및 지원</button>
          <button type="button" @click="logout">⇥　로그아웃</button>
        </footer>
      </aside>
      <section class="home-dashboard">
        <AsyncState
          v-if="boardState.loading || boardState.error"
          :loading="boardState.loading"
          :error="boardState.error"
          :retry="reloadBoard"
        />
        <template v-else>
          <div class="live-label">
            <i></i><b>회의 바로가기</b>
            <small>{{ boardState.meetings.length }}개의 회의가 진행 중입니다.</small>
          </div>
          <article v-if="boardState.activeMeeting.id" class="live-card home-live-card">
            <header>
              <div>
                <h2>
                  {{ boardState.activeMeeting.title }}
                  <small>{{ boardState.activeMeeting.status }}</small>
                </h2>
                <p>{{ boardState.activeMeeting.description }}</p>
              </div>
              <div class="meeting-avatars">
                <i v-for="avatar in boardState.activeMeeting.avatars" :key="avatar">{{ avatar }}</i>
              </div>
            </header>
            <footer>
              ◷ {{ boardState.activeMeeting.date }} · {{ boardState.activeMeeting.time }} 　♙
              {{ boardState.activeMeeting.participantCount }}명 참여 중
              <button type="button" @click="enterMeeting">회의 입장 →</button>
            </footer>
          </article>
          <article v-else class="live-card home-live-card">
            <header>
              <div><h2>진행 중인 회의가 없습니다.</h2></div>
            </header>
          </article>
          <BoardCalendar
            title="나의 일정"
            :calendar="boardState.calendar"
            @change-month="changeMonth"
          />
          <button
            class="home-meeting-fab"
            type="button"
            aria-label="새 회의 만들기"
            @click="showMeeting = true"
          >
            <span aria-hidden="true"></span>
          </button>
        </template>
      </section>
    </div>
  </main>
  <NewTeamModal v-if="showNewTeam" @close="showNewTeam = false" />
  <NewMeetingModal
    v-if="showMeeting"
    :workspaces="boardState.workspaces"
    :team-id="teamId"
    @close="showMeeting = false"
  />
  <HelpSupportModal v-if="showHelp" @close="showHelp = false" />
  <WorkspaceContextMenu
    v-if="menuWorkspace"
    :workspace="menuWorkspace"
    :position="menuPosition"
    @close="closeWorkspaceMenu"
    @select="handleWorkspaceMenuAction"
  />
  <InviteModal
    v-if="inviteWorkspace"
    :team-id="String(inviteWorkspace.id)"
    :members="boardState.members"
    @close="inviteWorkspace = null"
  />
  <BaseModal v-if="pendingWorkspaceAction" @close="closeWorkspaceConfirmation">
    <div class="workspace-confirmation">
      <small>SPACE CONFIRMATION</small>
      <h2>{{ workspaceConfirmation.title }}</h2>
      <p>{{ workspaceConfirmation.description }}</p>
      <div>
        <button
          class="outline-btn"
          type="button"
          :disabled="workspaceActionPending"
          @click="closeWorkspaceConfirmation"
        >
          취소
        </button>
        <button
          class="workspace-danger-confirm"
          type="button"
          :disabled="workspaceActionPending"
          @click="confirmWorkspaceAction"
        >
          {{ workspaceActionPending ? '처리 중...' : workspaceConfirmation.confirmLabel }}
        </button>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTopbar from '../../../shared/components/AppTopbar.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import HelpSupportModal from '../../../shared/components/HelpSupportModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../../auth/stores/authStore'
import { useUserPage } from '../../user/composables/useUserPage'
import { userStore } from '../../user/stores/userStore'
import BoardCalendar from '../components/BoardCalendar.vue'
import InviteModal from '../components/InviteModal.vue'
import NewMeetingModal from '../components/NewMeetingModal.vue'
import NewTeamModal from '../components/NewTeamModal.vue'
import WorkspaceContextMenu from '../components/WorkspaceContextMenu.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const router = useRouter()
const { notify } = useToast()
const { boardState, teamId, meetingId, changeMonth, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'activeMeeting', 'calendar']
})
const { userState } = useUserPage()
const query = ref('')
const showNewTeam = ref(false)
const showMeeting = ref(false)
const showHelp = ref(false)
const menuWorkspace = ref(null)
const menuPosition = ref({ x: 0, y: 0 })
const inviteWorkspace = ref(null)
const actionWorkspace = ref(null)
const pendingWorkspaceAction = ref('')
const workspaceActionPending = ref(false)
const draggingWorkspaceId = ref('')
const dropTargetWorkspaceId = ref('')
const dragDirection = ref('')
let searchTimer
let suppressWorkspaceClick = false

const workspaceConfirmation = computed(() => {
  const workspaceName = actionWorkspace.value?.name || '이 팀 스페이스'
  if (pendingWorkspaceAction.value === 'delete') {
    return {
      title: `${workspaceName}을 삭제할까요?`,
      description: '팀 스페이스와 하위 데이터는 삭제 후 되돌릴 수 없습니다.',
      confirmLabel: '삭제'
    }
  }
  return {
    title: `${workspaceName}에서 나갈까요?`,
    description: '다시 초대받기 전까지 이 팀 스페이스에 접근할 수 없습니다.',
    confirmLabel: '나가기'
  }
})

watch(query, (keyword) => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    try {
      await boardStore.loadWorkspaces(keyword)
    } catch (error) {
      notify(error?.message || '스페이스를 검색하지 못했습니다.')
    }
  }, 250)
})

onBeforeUnmount(() => clearTimeout(searchTimer))

const openWorkspace = (workspaceId) => {
  if (suppressWorkspaceClick) return
  router.push(`/teams/${workspaceId}/schedule`)
}

const openWorkspaceMenu = (event, workspace, source) => {
  event.preventDefault()
  event.stopPropagation()

  const menuWidth = 188
  const menuHeight = 224
  const edge = 8
  let x = event.clientX
  let y = event.clientY

  if (source === 'button') {
    const rect = event.currentTarget.getBoundingClientRect()
    x = rect.right - menuWidth
    y = rect.bottom + 6
  }

  menuPosition.value = {
    x: Math.max(edge, Math.min(x, window.innerWidth - menuWidth - edge)),
    y: Math.max(edge, Math.min(y, window.innerHeight - menuHeight - edge))
  }
  menuWorkspace.value = workspace
}

const closeWorkspaceMenu = () => {
  menuWorkspace.value = null
}

const handleWorkspaceMenuAction = async (action) => {
  const workspace = menuWorkspace.value
  closeWorkspaceMenu()
  if (!workspace) return

  if (action === 'open') {
    await router.push(`/teams/${workspace.id}/schedule`)
    return
  }
  if (action === 'members') {
    await router.push(`/teams/${workspace.id}/members`)
    return
  }
  if (action === 'settings') {
    await router.push(`/teams/${workspace.id}/settings`)
    return
  }
  if (action === 'invite') {
    try {
      await boardStore.loadMembers(workspace.id)
      inviteWorkspace.value = workspace
    } catch (error) {
      notify(error?.message || '멤버 정보를 불러오지 못했습니다.')
    }
    return
  }

  actionWorkspace.value = workspace
  pendingWorkspaceAction.value = action
}

const closeWorkspaceConfirmation = () => {
  if (workspaceActionPending.value) return
  pendingWorkspaceAction.value = ''
  actionWorkspace.value = null
}

const confirmWorkspaceAction = async () => {
  if (!actionWorkspace.value || !pendingWorkspaceAction.value) return
  workspaceActionPending.value = true
  try {
    if (pendingWorkspaceAction.value === 'delete') {
      await boardStore.deleteWorkspace(actionWorkspace.value.id)
      notify('팀 스페이스를 삭제했습니다.')
    } else {
      await boardStore.leaveWorkspace(actionWorkspace.value.id)
      notify('팀 스페이스에서 나갔습니다.')
    }
    pendingWorkspaceAction.value = ''
    actionWorkspace.value = null
  } catch (error) {
    notify(error?.message || '팀 스페이스 요청을 처리하지 못했습니다.')
  } finally {
    workspaceActionPending.value = false
  }
}

const startWorkspaceDrag = (event, workspaceId) => {
  closeWorkspaceMenu()
  if (query.value) {
    event.preventDefault()
    return
  }
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

const previewWorkspaceDrop = (targetWorkspaceId) => {
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) {
    dropTargetWorkspaceId.value = ''
    dragDirection.value = ''
    return
  }

  const workspaceIds = boardState.workspaces.map((workspace) => String(workspace.id))
  const sourceIndex = workspaceIds.indexOf(sourceId)
  const targetIndex = workspaceIds.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) return

  dropTargetWorkspaceId.value = targetId
  dragDirection.value = sourceIndex < targetIndex ? 'down' : 'up'
}

const dropWorkspace = async (targetWorkspaceId) => {
  if (query.value) return
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) return

  const nextOrder = boardState.workspaces.map((workspace) => String(workspace.id))
  const sourceIndex = nextOrder.indexOf(sourceId)
  const targetIndex = nextOrder.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) return

  nextOrder.splice(sourceIndex, 1)
  nextOrder.splice(targetIndex, 0, sourceId)
  try {
    await boardStore.reorderWorkspaces(nextOrder)
    notify('스페이스 순서를 저장했습니다.')
  } catch (error) {
    notify(error?.message || '스페이스 순서를 저장하지 못했습니다.')
  } finally {
    endWorkspaceDrag()
  }
}

const enterMeeting = () => {
  if (meetingId.value) router.push(`/meetings/${meetingId.value}`)
}

async function logout() {
  try {
    await authStore.logout()
  } catch (error) {
    notify(error?.message || '로그아웃하지 못했습니다.')
  } finally {
    boardStore.reset()
    userStore.reset()
    await router.replace('/login')
  }
}
</script>

<style scoped>
.workspace-add {
  display: grid;
  place-items: center;
  width: 100%;
  height: 58px;
  margin: 0;
  padding: 0;
  border-radius: 16px;
  background: #293753;
}

.workspace-add:hover {
  background: #30447e;
  transform: none;
}

.workspace-add > span {
  position: relative;
  display: block;
  width: 30px;
  height: 30px;
  transition: transform 0.24s ease;
}

.workspace-add > span::before,
.workspace-add > span::after {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 999px;
  background: currentColor;
  content: '';
  transform: translate(-50%, -50%);
}

.workspace-add > span::before {
  width: 28px;
  height: 3px;
}

.workspace-add > span::after {
  width: 3px;
  height: 28px;
}

.workspace-add:hover > span {
  transform: rotate(90deg) scale(1.08);
}

.workspace-item[draggable='true'] {
  cursor: grab;
}

.workspace-item[draggable='true']:active {
  cursor: grabbing;
}

.workspace-item.dragging {
  opacity: 0.45;
}

.workspace-item {
  position: relative;
  transition:
    transform 0.18s ease,
    opacity 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease;
}

.workspace-item > i {
  overflow: hidden;
}

.workspace-item > i img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.workspace-item:focus-visible {
  outline: 2px solid #8da1ff;
  outline-offset: 2px;
}

.workspace-more {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0 0 6px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #aeb7c9;
  font-size: 15px;
  letter-spacing: 1px;
  line-height: 1;
}

.workspace-more:hover,
.workspace-more:focus-visible {
  outline: 0;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.workspace-confirmation {
  display: grid;
  gap: 12px;
}

.workspace-confirmation > small {
  color: #566fea;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.5px;
}

.workspace-confirmation h2,
.workspace-confirmation p {
  margin: 0;
}

.workspace-confirmation p {
  color: #6d7587;
  font-size: 11px;
  line-height: 1.65;
}

.workspace-confirmation > div {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 10px;
}

.workspace-danger-confirm {
  min-width: 72px;
  padding: 8px 14px;
  border: 0;
  border-radius: 7px;
  background: #c84242;
  color: #fff;
  font-weight: 700;
}

.workspace-danger-confirm:hover {
  background: #ad3030;
}

.workspace-danger-confirm:disabled {
  cursor: wait;
  opacity: 0.65;
}

.workspace-item.drop-shift-up {
  transform: translateY(-8px);
  box-shadow: 0 10px 20px rgba(15, 24, 48, 0.18);
}

.workspace-item.drop-shift-down {
  transform: translateY(8px);
  box-shadow: 0 -10px 20px rgba(15, 24, 48, 0.18);
}

.home-meeting-fab {
  position: absolute;
  right: 16px;
  bottom: 12px;
  display: grid;
  place-items: center;
  width: 58px;
  height: 58px;
  padding: 0;
  border: 1px solid rgba(255, 255, 255, 0.55);
  border-radius: 50%;
  background: #6274e9;
  color: #fff;
  box-shadow: 0 10px 24px rgba(41, 57, 133, 0.28);
  cursor: pointer;
  pointer-events: auto;
  touch-action: manipulation;
  z-index: 30;
  transform: translateY(0) scale(1);
  transition:
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.home-meeting-fab::before {
  position: absolute;
  border-radius: 50%;
  content: '';
  inset: -10px;
}

.home-meeting-fab:hover,
.home-meeting-fab:focus-visible {
  background: #5366de;
  box-shadow: 0 15px 30px rgba(41, 57, 133, 0.38);
  outline: none;
  transform: scale(1.06);
}

.home-meeting-fab:focus-visible {
  box-shadow:
    0 0 0 4px rgba(98, 116, 233, 0.2),
    0 15px 30px rgba(41, 57, 133, 0.38);
}

.home-meeting-fab:active {
  box-shadow: 0 7px 16px rgba(41, 57, 133, 0.28);
  transform: scale(0.98);
}

.home-meeting-fab > span {
  position: relative;
  display: block;
  width: 25px;
  height: 25px;
  pointer-events: none;
  color: #fff;
  z-index: 1;
  transition: transform 0.24s ease;
}

.home-meeting-fab > span::before,
.home-meeting-fab > span::after {
  position: absolute;
  top: 50%;
  left: 50%;
  border-radius: 999px;
  background: #fff;
  content: '';
  transform: translate(-50%, -50%);
}

.home-meeting-fab > span::before {
  width: 24px;
  height: 2px;
}

.home-meeting-fab > span::after {
  width: 2px;
  height: 24px;
}

.home-meeting-fab:hover > span,
.home-meeting-fab:focus-visible > span {
  transform: rotate(90deg) scale(1.08);
}
</style>
