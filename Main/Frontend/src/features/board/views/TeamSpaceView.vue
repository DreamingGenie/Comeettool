<template>
  <TeamLayout active-section="schedule" :retry="reloadBoard">
    <header class="page-heading">
      <div>
        <span class="eyebrow">TEAM SCHEDULE</span>
        <h1>{{ boardState.team.name }} 팀 스페이스</h1>
        <p>진행 중인 회의와 팀 일정을 한눈에 확인하세요.</p>
      </div>
      <button class="outline-btn" type="button" @click="showInvite = true">초대 링크 공유</button>
    </header>
    <div class="live-label">
      <i></i><b>진행 중인 회의</b>
      <small>{{ boardState.meetings.length }}개의 회의가 진행 중입니다.</small>
    </div>
    <div class="meeting-cards">
      <article v-for="meeting in boardState.meetings" :key="meeting.id" class="live-card">
        <h2>{{ meeting.title }}　<small style="color:#15a866">{{ meeting.status }}</small></h2>
        <p>{{ meeting.description }}</p>
        <footer>
          ◷ {{ meeting.date }} · {{ meeting.time }}
          　♙ {{ meeting.participantCount }}명 참여 중
          <button type="button" @click="$router.push(`/meetings/${meeting.id}`)">회의 입장 →</button>
        </footer>
      </article>
      <button class="create-card" type="button" @click="showMeeting = true">
        <i>＋</i><b>새 회의 만들기</b><span>팀원들과 바로 회의를 시작하세요.</span>
      </button>
    </div>
    <BoardCalendar :calendar="boardState.calendar" @change-month="changeMonth" />
  </TeamLayout>
  <InviteModal
    v-if="showInvite"
    :team-id="teamId"
    :members="boardState.inviteMembers"
    @close="showInvite = false"
  />
  <NewMeetingModal
    v-if="showMeeting"
    :workspaces="boardState.workspaces"
    :team-id="teamId"
    @close="showMeeting = false"
  />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import BoardCalendar from '../components/BoardCalendar.vue'
import InviteModal from '../components/InviteModal.vue'
import NewMeetingModal from '../components/NewMeetingModal.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const { boardState, teamId, changeMonth, reloadBoard } = useBoardPage({
  resources: [
    'workspaces',
    'team',
    'activeMeeting',
    'calendar',
    'inviteMembers'
  ]
})
const showInvite = ref(false)
const showMeeting = ref(false)
let meetingCountRefreshTimer = null

onMounted(() => {
  meetingCountRefreshTimer = window.setInterval(() => {
    boardStore.loadMeetings(teamId.value, { silent: true }).catch(() => undefined)
  }, 5000)
})

onBeforeUnmount(() => {
  if (meetingCountRefreshTimer) window.clearInterval(meetingCountRefreshTimer)
})
</script>
