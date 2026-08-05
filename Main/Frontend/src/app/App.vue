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
    // 스페이스 초대 하나가 실패해도 회의 초대는 표시되도록 서로 독립적으로 처리한다.
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
  // 회의 초대는 수락/거절이 없다. 누르면 곧바로 회의방으로 이동한다.
  join(meetingId) {
    notificationStore.dismissMeetingInvite(meetingId)
    return router.push(`/meetings/${meetingId}`)
  }
})

// 알림 폴링이 워크스페이스 로드보다 먼저 돌 수 있다. 목록이 채워지는 시점에 한 번 더 계산한다.
watch(
  () => boardStore.state.workspaces.length,
  count => {
    if (count) loadMeetingInvites().catch(() => undefined)
  }
)
</script>
