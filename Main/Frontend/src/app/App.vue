<template>
  <RouterView />
  <AppToast />
</template>

<script setup>
import { provide } from 'vue'
import { RouterView } from 'vue-router'
import { boardStore } from '../features/board/stores/boardStore'
import { notificationStore } from '../features/notification/stores/notificationStore'
import AppToast from '../shared/components/AppToast.vue'
import { notificationContextKey } from '../shared/injection/notificationContext'

provide(notificationContextKey, {
  state: notificationStore.state,
  load: () => notificationStore.loadInvitations(),
  async accept(invitationId) {
    await notificationStore.acceptInvitation(invitationId)
    await boardStore.loadWorkspaces()
  },
  reject: (invitationId) => notificationStore.rejectInvitation(invitationId)
})
</script>
