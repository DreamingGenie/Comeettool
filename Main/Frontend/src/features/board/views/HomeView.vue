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
          <button
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
            type="button"
            :draggable="!query"
            :aria-grabbed="draggingWorkspaceId === String(workspace.id)"
            @click="openWorkspace(workspace.id)"
            @dragstart="startWorkspaceDrag($event, workspace.id)"
            @dragend="endWorkspaceDrag"
            @dragenter.prevent="previewWorkspaceDrop(workspace.id)"
            @dragover.prevent
            @drop.prevent="dropWorkspace(workspace.id)"
          >
            <i :style="workspace.color ? { backgroundColor: workspace.color } : undefined">
              {{ workspace.badge }}
            </i>
            <span>
              <b>{{ workspace.name }}</b>
              <small>{{ workspace.role }} · 멤버 {{ workspace.members }}명</small>
            </span>
            <em>•••</em>
          </button>
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
          <button type="button" @click="showHelp = true">
            ⓘ　도움말 및 지원
          </button>
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
              ◷ {{ boardState.activeMeeting.date }} · {{ boardState.activeMeeting.time }}
              　♙ {{ boardState.activeMeeting.participantCount }}명 참여 중
              <button type="button" @click="enterMeeting">회의 입장 →</button>
            </footer>
          </article>
          <article v-else class="live-card home-live-card">
            <header><div><h2>진행 중인 회의가 없습니다.</h2></div></header>
          </article>
          <BoardCalendar
            title="나의 일정"
            :calendar="boardState.calendar"
            @change-month="changeMonth"
          />
          <button class="floating-add" type="button" aria-label="새 회의 만들기" @click="showMeeting = true">
            ＋
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
</template>

<script setup>
import { onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppTopbar from '../../../shared/components/AppTopbar.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import HelpSupportModal from '../../../shared/components/HelpSupportModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../../auth/stores/authStore'
import { useUserPage } from '../../user/composables/useUserPage'
import { userStore } from '../../user/stores/userStore'
import BoardCalendar from '../components/BoardCalendar.vue'
import NewMeetingModal from '../components/NewMeetingModal.vue'
import NewTeamModal from '../components/NewTeamModal.vue'
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
const draggingWorkspaceId = ref('')
const dropTargetWorkspaceId = ref('')
const dragDirection = ref('')
let searchTimer
let suppressWorkspaceClick = false

watch(query, keyword => {
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

const openWorkspace = workspaceId => {
  if (suppressWorkspaceClick) return
  router.push(`/teams/${workspaceId}/schedule`)
}

const startWorkspaceDrag = (event, workspaceId) => {
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

const previewWorkspaceDrop = targetWorkspaceId => {
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) {
    dropTargetWorkspaceId.value = ''
    dragDirection.value = ''
    return
  }

  const workspaceIds = boardState.workspaces.map(workspace => String(workspace.id))
  const sourceIndex = workspaceIds.indexOf(sourceId)
  const targetIndex = workspaceIds.indexOf(targetId)
  if (sourceIndex < 0 || targetIndex < 0) return

  dropTargetWorkspaceId.value = targetId
  dragDirection.value = sourceIndex < targetIndex ? 'down' : 'up'
}

const dropWorkspace = async targetWorkspaceId => {
  if (query.value) return
  const sourceId = draggingWorkspaceId.value
  const targetId = String(targetWorkspaceId)
  if (!sourceId || sourceId === targetId) return

  const nextOrder = boardState.workspaces.map(workspace => String(workspace.id))
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
  transition: transform 0.18s ease, opacity 0.18s ease,
    background-color 0.18s ease, box-shadow 0.18s ease;
}

.workspace-item.drop-shift-up {
  transform: translateY(-8px);
  box-shadow: 0 10px 20px rgba(15, 24, 48, 0.18);
}

.workspace-item.drop-shift-down {
  transform: translateY(8px);
  box-shadow: 0 -10px 20px rgba(15, 24, 48, 0.18);
}
</style>
