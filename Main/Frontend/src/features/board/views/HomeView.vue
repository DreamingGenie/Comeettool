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
            v-for="workspace in filteredWorkspaces"
            :key="workspace.id"
            class="workspace-item"
            type="button"
            @click="router.push(`/teams/${workspace.id}/schedule`)"
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
            <small>{{ boardState.activeMeeting.id ? 1 : 0 }}개의 회의가 진행 중입니다.</small>
          </div>
          <article class="live-card home-live-card">
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
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTopbar from '../../../shared/components/AppTopbar.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import HelpSupportModal from '../../../shared/components/HelpSupportModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../../auth/stores/authStore'
import { useUserPage } from '../../user/composables/useUserPage'
import BoardCalendar from '../components/BoardCalendar.vue'
import NewMeetingModal from '../components/NewMeetingModal.vue'
import NewTeamModal from '../components/NewTeamModal.vue'
import { useBoardPage } from '../composables/useBoardPage'

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
const filteredWorkspaces = computed(() => {
  const keyword = query.value.toLowerCase()
  return boardState.workspaces.filter(item => item.name.toLowerCase().includes(keyword))
})

const enterMeeting = () => router.push(`/meetings/${meetingId.value}`)

async function logout() {
  try {
    await authStore.logout()
    await router.push('/')
  } catch (error) {
    notify(error?.message || '로그아웃하지 못했습니다.')
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
</style>
