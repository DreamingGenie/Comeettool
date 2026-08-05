<template>
  <div ref="root" class="notification-bell">
    <button
      class="notification-trigger"
      type="button"
      :aria-expanded="open"
      aria-label="초대 알림"
      @click="toggle"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9" />
        <path d="M10 21h4" />
      </svg>
      <span v-if="count" class="notification-count">{{ countLabel }}</span>
    </button>

    <section v-if="open" class="notification-panel" aria-label="초대 알림 목록">
      <header>
        <div>
          <small>NOTIFICATIONS</small>
          <h2>초대 알림</h2>
        </div>
        <button type="button" :disabled="loading" @click="$emit('refresh')">새로고침</button>
      </header>

      <div v-if="loading && !hasAnyNotification" class="notification-state">
        초대 알림을 불러오는 중입니다.
      </div>
      <div v-else-if="error && !hasAnyNotification" class="notification-state error">
        <p>{{ error }}</p>
        <button type="button" @click="$emit('refresh')">다시 시도</button>
      </div>
      <div v-else-if="!hasAnyNotification" class="notification-state">
        <span class="notification-empty-icon">✓</span>
        <b>새로운 초대가 없습니다.</b>
        <p>회의 초대나 팀 스페이스 초대가 도착하면 여기에 표시됩니다.</p>
      </div>
      <div v-else class="notification-list">
        <article
          v-for="invite in meetingInvites"
          :key="`meeting-${invite.meetingId}`"
          class="is-meeting"
        >
          <i class="meeting-mark" aria-hidden="true">🔴</i>
          <div>
            <b>{{ invite.title }}</b>
            <p>{{ meetingDescription(invite) }}</p>
            <time>{{ formatDate(invite.createdAt) }}</time>
          </div>
          <span class="notification-actions">
            <button type="button" @click="joinMeeting(invite.meetingId)">
              바로 참가하기
            </button>
          </span>
        </article>

        <article v-for="invitation in invitations" :key="invitation.invitationId">
          <i>{{ invitation.spaceName?.trim().slice(0, 1) || '팀' }}</i>
          <div>
            <b>{{ invitation.spaceName }}</b>
            <p>{{ invitation.inviterNickname || '팀 관리자' }}님이 팀 스페이스에 초대했습니다.</p>
            <time>{{ formatDate(invitation.createdAt) }}</time>
          </div>
          <span class="notification-actions">
            <button
              class="reject"
              type="button"
              :disabled="isProcessing(invitation.invitationId)"
              @click="$emit('reject', invitation.invitationId)"
            >
              {{ rejectingId === invitation.invitationId ? '거절 중' : '거절' }}
            </button>
            <button
              type="button"
              :disabled="isProcessing(invitation.invitationId)"
              @click="$emit('accept', invitation.invitationId)"
            >
              {{ acceptingId === invitation.invitationId ? '수락 중' : '수락' }}
            </button>
          </span>
        </article>
        <p v-if="error" class="notification-inline-error">{{ error }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

const props = defineProps({
  invitations: { type: Array, default: () => [] },
  meetingInvites: { type: Array, default: () => [] },
  loading: Boolean,
  acceptingId: { type: String, default: '' },
  rejectingId: { type: String, default: '' },
  error: { type: String, default: '' }
})

const emit = defineEmits(['refresh', 'accept', 'reject', 'join'])

const root = ref(null)
const open = ref(false)
const count = computed(() => props.invitations.length + props.meetingInvites.length)
const countLabel = computed(() => (count.value > 99 ? '99+' : String(count.value)))
const hasAnyNotification = computed(
  () => Boolean(props.invitations.length || props.meetingInvites.length)
)

function toggle() {
  open.value = !open.value
}

function meetingDescription(invite) {
  const parts = []
  if (invite.spaceName) parts.push(invite.spaceName)
  parts.push(`참여자 ${invite.participantCount}명`)
  return `${parts.join(' · ')} · 진행 중인 회의입니다.`
}

function joinMeeting(meetingId) {
  open.value = false
  emit('join', meetingId)
}

function isProcessing(invitationId) {
  return props.acceptingId === invitationId || props.rejectingId === invitationId
}

function closeOnOutside(event) {
  if (open.value && !root.value?.contains(event.target)) open.value = false
}

function closeOnEscape(event) {
  if (event.key === 'Escape') open.value = false
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return new Intl.DateTimeFormat('ko-KR', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  }).format(date)
}

onMounted(() => {
  document.addEventListener('click', closeOnOutside)
  document.addEventListener('keydown', closeOnEscape)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', closeOnOutside)
  document.removeEventListener('keydown', closeOnEscape)
})
</script>

<style scoped>
.notification-bell {
  position: relative;
}

.notification-trigger {
  position: relative;
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
  transition:
    background 0.2s,
    border-color 0.2s,
    transform 0.2s;
}

.notification-trigger:hover {
  border-color: rgba(255, 255, 255, 0.58);
  background: rgba(255, 255, 255, 0.14);
  transform: translateY(-1px);
}

.notification-trigger svg {
  width: 19px;
  height: 19px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.notification-count {
  position: absolute;
  top: -5px;
  right: -5px;
  min-width: 17px;
  height: 17px;
  padding: 0 4px;
  display: grid;
  place-items: center;
  border: 2px solid #172d52;
  border-radius: 10px;
  background: #ff4d67;
  color: #fff;
  font-size: 9px;
  font-weight: 800;
  line-height: 1;
}

.notification-panel {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: min(360px, calc(100vw - 24px));
  overflow: hidden;
  border: 1px solid #dfe4ef;
  border-radius: 16px;
  background: #fff;
  color: #182238;
  box-shadow: 0 18px 50px rgba(13, 28, 59, 0.22);
  z-index: 80;
}

.notification-panel header {
  min-height: 76px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-bottom: 1px solid #e9ecf3;
}

.notification-panel header small {
  color: #566fea;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.4px;
}

.notification-panel h2 {
  margin: 3px 0 0;
  font-size: 18px;
}

.notification-panel header button {
  border: 0;
  background: transparent;
  color: #697287;
  font-size: 11px;
}

.notification-state {
  min-height: 190px;
  padding: 32px 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  text-align: center;
  color: #697287;
}

.notification-state p {
  margin: 0;
  font-size: 12px;
}

.notification-state button {
  margin-top: 6px;
  padding: 7px 12px;
  border: 1px solid #cfd5e3;
  border-radius: 7px;
  background: #fff;
}

.notification-empty-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  background: #eef1ff;
  color: #566fea;
  font-weight: 800;
}

.notification-list {
  max-height: 390px;
  overflow-y: auto;
}

.notification-list article {
  display: grid;
  grid-template-columns: 38px 1fr auto;
  gap: 11px;
  align-items: start;
  padding: 16px 18px;
  border-bottom: 1px solid #edf0f5;
}

.notification-list article > i {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  background: #eef1ff;
  color: #566fea;
  font-style: normal;
  font-weight: 800;
}

.notification-list article.is-meeting {
  background: #fff7f8;
}

.notification-list article.is-meeting > i.meeting-mark {
  background: #ffe9ec;
  font-size: 13px;
}

.notification-list article.is-meeting .notification-actions button {
  background: #e5405a;
  white-space: nowrap;
}

.notification-list article b {
  font-size: 13px;
}

.notification-list article p {
  margin: 3px 0 6px;
  color: #697287;
  font-size: 11px;
  line-height: 1.5;
}

.notification-list time {
  color: #9299a8;
  font-size: 9px;
}

.notification-actions {
  align-self: center;
  display: flex;
  gap: 5px;
}

.notification-actions button {
  align-self: center;
  padding: 7px 11px;
  border: 0;
  border-radius: 7px;
  background: #566fea;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
}

.notification-actions button.reject {
  border: 1px solid #d6dbe6;
  background: #fff;
  color: #697287;
}

.notification-actions button:disabled {
  opacity: 0.55;
}

.notification-inline-error {
  margin: 0;
  padding: 10px 18px;
  background: #fff3f5;
  color: #c63c52;
  font-size: 11px;
}

@media (max-width: 520px) {
  .notification-panel {
    position: fixed;
    top: 64px;
    right: 8px;
    left: 8px;
    width: auto;
  }
}
</style>
