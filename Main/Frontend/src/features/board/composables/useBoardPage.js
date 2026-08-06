import { computed, onMounted, unref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { boardStore } from '../stores/boardStore'

const teamScopedResources = new Set([
  'team',
  'members',
  'activeMeeting',
  'calendar',
  'inviteMembers',
  'teamRoles',
  'archive'
])

export function useBoardPage(options = {}) {
  const route = useRoute()
  const resources = options.resources || []
  const teamId = computed(() => String(route.params.teamId || ''))
  const meetingId = computed(() =>
    String(
      route.params.meetingId ||
      boardStore.state.currentMeetingId ||
      boardStore.state.activeMeeting.id ||
      ''
    )
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
  if (resources.some(resource => teamScopedResources.has(resource))) {
    watch(teamId, loadSafely)
  }
  if (resources.includes('meetingRoom')) {
    watch(
      () => String(route.params.meetingId || ''),
      nextMeetingId => {
        if (nextMeetingId && nextMeetingId !== boardStore.state.currentMeetingId) {
          loadSafely()
        }
      }
    )
  }
  if (resources.includes('archive')) watch(archiveSection, loadSafely)

  return {
    boardState: boardStore.state,
    teamId,
    meetingId,
    reloadBoard: loadSafely,
    changeMonth
  }
}
