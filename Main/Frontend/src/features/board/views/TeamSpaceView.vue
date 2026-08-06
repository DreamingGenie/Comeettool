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
        <h2>
          {{ meeting.title }}　<small style="color: #15a866">{{ meeting.status }}</small>
        </h2>
        <p>{{ meeting.description }}</p>
        <footer>
          ◷ {{ meeting.date }} · {{ meeting.time }} 　♙ {{ meeting.participantCount }}명 참여 중
          <button type="button" @click="enterMeeting(meeting.id)">
            회의 입장 →
          </button>
        </footer>
      </article>
      <button class="create-card" type="button" @click="openMeetingCreation">
        <i>＋</i><b>새 회의 만들기</b><span>팀원들과 바로 회의를 시작하세요.</span>
      </button>
    </div>
    <BoardCalendar
      :calendar="boardState.calendar"
      editable
      @change-month="changeMonth"
      @select-date="openCreateSchedule"
      @select-event="openEditSchedule"
    />
  </TeamLayout>
  <InviteModal
    v-if="showInvite"
    :team-id="teamId"
    :members="boardState.team.members"
    @close="showInvite = false"
  />
  <NewMeetingModal
    v-if="showMeeting"
    :workspaces="boardState.workspaces"
    :team-id="teamId"
    team-locked
    @close="showMeeting = false"
  />
  <ScheduleModal
    v-if="scheduleModal.open"
    :event="scheduleModal.event"
    :year="boardState.calendar.year"
    :month="boardState.calendar.month"
    :day="scheduleModal.day"
    :pending="schedulePending"
    @close="closeScheduleModal"
    @save="saveSchedule"
    @delete="deleteSchedule"
  />
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import BoardCalendar from '../components/BoardCalendar.vue'
import InviteModal from '../components/InviteModal.vue'
import NewMeetingModal from '../components/NewMeetingModal.vue'
import ScheduleModal from '../components/ScheduleModal.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { meetingDocumentMode } from '../composables/useMeetingDocumentMode'
import { boardStore } from '../stores/boardStore'
import { useToast } from '../../../shared/composables/useToast'

const { boardState, teamId, changeMonth, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team', 'activeMeeting', 'calendar']
})
const showInvite = ref(false)
const showMeeting = ref(false)
const scheduleModal = ref({ open: false, day: 1, event: null })
const schedulePending = ref(false)
const { notify } = useToast()
const router = useRouter()
let meetingCountRefreshTimer = null

function enterMeeting(meetingId) {
  if (!meetingDocumentMode.allowMeetingAction(notify)) return
  router.push(`/meetings/${meetingId}`)
}

function openMeetingCreation() {
  if (!meetingDocumentMode.allowMeetingAction(notify)) return
  showMeeting.value = true
}

function openCreateSchedule(day) {
  scheduleModal.value = { open: true, day, event: null }
}

function openEditSchedule(event) {
  scheduleModal.value = { open: true, day: event.day, event }
}

function closeScheduleModal() {
  scheduleModal.value = { open: false, day: 1, event: null }
}

async function saveSchedule(event) {
  schedulePending.value = true
  try {
    if (scheduleModal.value.event) {
      await boardStore.updateSchedule(teamId.value, event.id, event)
      notify('일정을 수정했습니다.')
    } else {
      await boardStore.createSchedule(teamId.value, event)
      notify('일정을 추가했습니다.')
    }
    closeScheduleModal()
  } catch (error) {
    notify(error?.message || '일정을 저장하지 못했습니다.')
  } finally {
    schedulePending.value = false
  }
}

async function deleteSchedule(event) {
  schedulePending.value = true
  try {
    await boardStore.deleteSchedule(teamId.value, event.id)
    notify('일정을 삭제했습니다.')
    closeScheduleModal()
  } catch (error) {
    notify(error?.message || '일정을 삭제하지 못했습니다.')
  } finally {
    schedulePending.value = false
  }
}

onMounted(() => {
  meetingCountRefreshTimer = window.setInterval(() => {
    boardStore.loadMeetings(teamId.value, { silent: true }).catch(() => undefined)
  }, 5000)
})

onBeforeUnmount(() => {
  if (meetingCountRefreshTimer) window.clearInterval(meetingCountRefreshTimer)
})
</script>
