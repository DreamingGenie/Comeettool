import { computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { boardStore } from '../stores/boardStore'

export function useBoardPage() {
  const route = useRoute()
  const teamId = computed(() => String(route.params.teamId || 'a707'))
  const meetingId = computed(() =>
    String(route.params.meetingId || boardStore.state.activeMeeting.id || 'be-team-meeting')
  )

  const load = () =>
    boardStore.load({
      teamId: teamId.value,
      meetingId: meetingId.value
    })
  const loadSafely = () => load().catch(() => undefined)

  const changeMonth = async delta => {
    let year = boardStore.state.calendar.year
    let month = boardStore.state.calendar.month + delta
    if (month < 1) {
      month = 12
      year -= 1
    }
    if (month > 12) {
      month = 1
      year += 1
    }
    await boardStore.loadCalendar(teamId.value, year, month)
  }

  onMounted(loadSafely)
  watch([teamId, meetingId], loadSafely)

  return {
    boardState: boardStore.state,
    teamId,
    meetingId,
    reloadBoard: loadSafely,
    changeMonth
  }
}
