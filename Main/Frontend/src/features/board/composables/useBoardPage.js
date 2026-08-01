import { computed, onMounted, unref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { boardStore } from '../stores/boardStore'

export function useBoardPage(options = {}) {
  const route = useRoute()
  const resources = options.resources || []
  const teamId = computed(() => String(route.params.teamId || ''))
  const meetingId = computed(() =>
    String(route.params.meetingId || boardStore.state.activeMeeting.id || '')
  )
  const archiveSection = computed(() => {
    const section = typeof options.section === 'function'
      ? options.section()
      : unref(options.section)
    return section || 'documents'
  })

  const load = () =>
    boardStore.loadResources(resources, {
      teamId: teamId.value,
      meetingId: meetingId.value,
      year: boardStore.state.calendar.year,
      month: boardStore.state.calendar.month,
      section: archiveSection.value
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
  watch(teamId, loadSafely)
  if (resources.includes('meetingRoom')) watch(meetingId, loadSafely)
  if (resources.includes('archive')) watch(archiveSection, loadSafely)

  return {
    boardState: boardStore.state,
    teamId,
    meetingId,
    reloadBoard: loadSafely,
    changeMonth
  }
}
