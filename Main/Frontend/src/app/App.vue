<template>
  <RouterView />
  <AppToast />
</template>

<script setup>
import { provide, watch } from 'vue'
import { RouterView, useRouter } from 'vue-router'
import { boardStore } from '../features/board/stores/boardStore'
import { notificationStore } from '../features/notification/stores/notificationStore'
import AppToast from '../shared/components/AppToast.vue'
import { notificationContextKey } from '../shared/injection/notificationContext'

const router = useRouter()

const loadMeetingInvites = () =>
  notificationStore.loadMeetingInvites({
    spaces: boardStore.state.workspaces,
    excludeMeetingId: boardStore.state.currentMeetingId
  })

provide(notificationContextKey, {
  state: notificationStore.state,
  async load() {
    const results = await Promise.allSettled([
      notificationStore.loadInvitations(),
      loadMeetingInvites()
    ])
    const failure = results.find(result => result.status === 'rejected')
    if (failure) throw failure.reason
  },
  async accept(invitationId) {
    await notificationStore.acceptInvitation(invitationId)
    await boardStore.loadWorkspaces()
  },
  reject: (invitationId) => notificationStore.rejectInvitation(invitationId),
  join(meetingId) {
    notificationStore.dismissMeetingInvite(meetingId)
    return router.push(`/meetings/${meetingId}`)
  }
})

watch(
  () => boardStore.state.workspaces.length,
  count => {
    if (count) loadMeetingInvites().catch(() => undefined)
  }
)
</script>
