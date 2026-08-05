<template>
  <header class="topbar">
    <AppSparkles />
    <AppLogo to="/home" />
    <div class="topbar-actions">
      <AppNotificationBell
        v-if="notification"
        :invitations="notification.state.invitations"
        :meeting-invites="notification.state.meetingInvites"
        :loading="notification.state.loading"
        :accepting-id="notification.state.acceptingId"
        :rejecting-id="notification.state.rejectingId"
        :error="notification.state.error"
        @refresh="loadNotifications"
        @accept="acceptInvitation"
        @reject="rejectInvitation"
        @join="joinMeeting"
      />
      <button class="user-pill" type="button" @click="$router.push('/profile')">
        <img
          v-if="user?.profileImage && !profileImageFailed"
          class="user-avatar"
          :src="user.profileImage"
          :alt="`${user?.nickname || '사용자'} 프로필`"
          @error="profileImageFailed = true"
        />
        <i v-else class="user-avatar" :style="{ backgroundColor: user?.userColor || '#5f6fe5' }">
          {{ avatarLabel }}
        </i>
        {{ user?.nickname || '' }}
      </button>
    </div>
  </header>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import AppNotificationBell from './AppNotificationBell.vue'
import AppLogo from './AppLogo.vue'
import AppSparkles from './AppSparkles.vue'
import { notificationContextKey } from '../injection/notificationContext'

const props = defineProps({
  user: { type: Object, default: () => ({}) }
})

const profileImageFailed = ref(false)
const notification = inject(notificationContextKey, null)
let notificationTimer
const avatarLabel = computed(
  () => props.user?.nickname?.trim().slice(0, 1) || props.user?.avatarText || ''
)

watch(
  () => props.user?.profileImage,
  () => {
    profileImageFailed.value = false
  }
)

async function loadNotifications() {
  try {
    await notification?.load()
  } catch {
    // 오류 상태는 알림 패널에서 표시한다.
  }
}

async function acceptInvitation(invitationId) {
  try {
    await notification?.accept(invitationId)
  } catch {
    // 오류 상태는 알림 패널에서 표시한다.
  }
}

async function rejectInvitation(invitationId) {
  try {
    await notification?.reject(invitationId)
  } catch {
    // 오류 상태는 알림 패널에서 표시한다.
  }
}

async function joinMeeting(meetingId) {
  try {
    await notification?.join(meetingId)
  } catch {
    // 오류 상태는 알림 패널에서 표시한다.
  }
}

onMounted(() => {
  loadNotifications()
  window.addEventListener('focus', loadNotifications)
  notificationTimer = window.setInterval(loadNotifications, 30000)
})

onBeforeUnmount(() => {
  window.removeEventListener('focus', loadNotifications)
  window.clearInterval(notificationTimer)
})
</script>

<style scoped>
.topbar {
  position: relative;
  overflow: visible;
}

.topbar > :not(.app-sparkles) {
  position: relative;
  z-index: 1;
}

.user-pill {
  border-color: rgba(255, 255, 255, 0.38);
  background: rgba(255, 255, 255, 0.04);
}

.topbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-avatar {
  width: 24.8px;
  height: 24.8px;
  flex: 0 0 24.8px;
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 2px 6px rgba(5, 15, 38, 0.2);
}

.user-pill i.user-avatar {
  color: #fff;
  font-size: 9.6px;
  letter-spacing: -0.2px;
}
</style>
