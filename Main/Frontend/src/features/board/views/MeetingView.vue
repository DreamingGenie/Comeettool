<template>
  <main ref="meetingRoomElement" class="meeting-room dark">
    <header class="meeting-top">
      <h1>
        🔴　{{ boardState.activeMeeting.roomTitle || '회의' }}
        <small class="connection-status" :class="liveKitStatus">
          {{ connectionStatusLabel }}
        </small>
        <small v-if="vadStatus === 'recording'" class="vad-status">
          VAD {{ vadPendingUploads > 0 ? `업로드 ${vadPendingUploads}` : '감지 중' }}
        </small>
      </h1>
      <div class="meeting-top-actions">
        <button class="participant-count" type="button" @click="modal = 'participants'">
          <span>
            <i v-for="participant in participantPreview" :key="participant.id">
              {{ participant.avatarText.slice(0, 1) }}
            </i>
          </span>
          <b>{{ liveParticipantCount }}명 참가 중</b>
        </button>
        <button class="meeting-help" type="button" @click="modal = 'help'">
          <i>?</i><b>도움말</b>
        </button>
      </div>
    </header>
    <div class="meeting-content" :class="{ 'chat-closed': !isChatPanelOpen }">
      <section class="video-column">
        <div
          class="video-grid"
          :class="videoLayoutClass"
          :aria-label="`참가자 영상 ${visibleParticipants.length}명`"
        >
          <article
            v-for="participant in visibleParticipants"
            :key="participant.id"
            class="video-tile"
            :class="{
              'is-speaking': !participant.isScreenShare && activeSpeakerIdentities.includes(participant.livekitIdentity),
              'is-pinned': pinnedParticipantId === participant.id,
              'is-screen-share': participant.isScreenShare
            }"
          >
            <div
              :ref="element => mountParticipantMedia(participant.mediaKey, element)"
              class="participant-media"
            ></div>
            <button
              class="meeting-pin"
              :class="{ 'is-pinned': pinnedParticipantId === participant.id }"
              type="button"
              :aria-label="`${participant.displayName} 화면 고정`"
              :aria-pressed="pinnedParticipantId === participant.id"
              @click="togglePin(participant)"
            ></button>
            <div class="participant-badge" :class="{ 'is-muted': participant.muted }">
              <button
                v-if="!participant.isScreenShare"
                type="button"
                :aria-label="`${participant.displayName} 마이크 ${participant.muted ? '켜기' : '음소거'}`"
                @click="toggleParticipantMic(participant)"
              >
                {{ participant.muted ? '🔇' : '🎙' }}
              </button>
              <i v-else class="screen-share-mark">↗</i>
              <span>{{ participant.displayName }}</span>
            </div>
          </article>
          <nav
            v-if="videoPageCount > 1"
            class="video-pagination"
            aria-label="참가자 영상 페이지"
          >
            <button
              type="button"
              aria-label="이전 참가자 페이지"
              :disabled="currentVideoPage === 0"
              @click="currentVideoPage -= 1"
            >
              ‹
            </button>
            <span>{{ currentVideoPage + 1 }} / {{ videoPageCount }}</span>
            <button
              type="button"
              aria-label="다음 참가자 페이지"
              :disabled="currentVideoPage >= videoPageCount - 1"
              @click="currentVideoPage += 1"
            >
              ›
            </button>
          </nav>
        </div>
        <footer class="controls">
          <span class="meeting-agenda">
            {{ boardState.activeMeeting.agendaTime }}　| {{ boardState.activeMeeting.roomTitle }}
          </span>
          <div class="control-items">
            <button
              v-for="control in meetingControls"
              :key="control.id"
              class="meeting-control"
              :class="{ active: activeControls.has(control.id) }"
              type="button"
              @click="handleControl(control.id)"
            >
              <span>
                <img :src="`/assets/icons/${control.id}.svg`" alt="" aria-hidden="true" />
              </span>
              <small>{{ control.label }}</small>
            </button>
          </div>
          <i class="control-divider"></i>
          <button
            class="hangup"
            type="button"
            :disabled="exiting"
            :aria-label="isHost ? '회의 종료' : '회의 나가기'"
            @click="exitMeeting"
          >
            <img src="/assets/icons/hangup.svg" alt="" aria-hidden="true" />
          </button>

          <section v-if="showMore" class="meeting-more-menu">
            <header><b>더보기</b><small>현재 사용할 수 있는 기능</small></header>
            <button
              type="button"
              :class="{ enabled: isFullscreen }"
              @click="toggleFullscreen"
            >
              <i>⛶</i>
              <span><b>전체 화면</b><small>회의 화면을 브라우저 전체 화면으로 보기</small></span>
              <em>{{ isFullscreen ? '✓' : '›' }}</em>
            </button>
          </section>
        </footer>
      </section>

      <aside v-if="isChatPanelOpen" class="chat-panel">
        <nav class="chat-tabs">
          <button type="button" :class="{ active: activeTab === 'chat' }" @click="selectTab('chat')">
            채팅
          </button>
          <button type="button" :class="{ active: activeTab === 'direct' }" @click="selectTab('direct')">
            다이렉트
          </button>
        </nav>
        <section ref="messageList" class="messages">
          <template v-if="activeTab === 'chat'">
            <article
              v-for="message in boardState.meetingRoom.chatMessages"
              :key="message.id || `${message.sender}-${message.time}-${message.body}`"
              :class="{ me: message.mine }"
            >
              <b>{{ message.sender }}　<small>{{ message.time }}</small></b>
              <p>{{ message.body }}</p>
            </article>
          </template>
          <div v-else-if="!activeContact" class="direct-list">
            <header>
              <b>다이렉트 메시지</b>
              <small>온라인 {{ boardState.meetingRoom.directContacts.length }}명</small>
            </header>
            <button
              v-for="contact in boardState.meetingRoom.directContacts"
              :key="contact.id"
              class="direct-contact"
              type="button"
              @click="activeContactId = contact.id"
            >
              <i>{{ contact.avatarText }}</i>
              <span><b>{{ contact.name }}</b><small>{{ contact.preview }}</small></span>
              <em>{{ contact.time }}</em>
            </button>
          </div>
          <div v-else class="direct-conversation">
            <header>
              <button type="button" @click="activeContactId = ''">←</button>
              <div><b>{{ activeContact.name }}</b><small>온라인</small></div>
            </header>
            <article
              v-for="message in activeContact.messages"
              :key="message.id || `${message.sender}-${message.time}-${message.body}`"
              :class="{ me: message.mine }"
            >
              <b>{{ message.sender }}　<small>{{ message.time }}</small></b>
              <p>{{ message.body }}</p>
            </article>
          </div>
        </section>
        <form class="chat-input" @submit.prevent="send">
          <input
            v-model="draft"
            :disabled="activeTab === 'direct' && !activeContact"
            :placeholder="messagePlaceholder"
          />
          <button :disabled="!draft.trim()">▷</button>
        </form>
      </aside>
    </div>
  </main>

  <BaseModal v-if="modal === 'participants'" modal-class="meeting-info-modal" @close="closeParticipantsModal">
    <small>MEETING PARTICIPANTS</small>
    <h2>참가자 {{ liveParticipantCount }}명</h2>
    <div class="meeting-member-list">
      <article
        v-for="participant in liveParticipants"
        :key="participant.id"
        class="meeting-member-row"
        :class="{ 'is-host': participant.isHost }"
      >
        <i>{{ participant.avatarText.slice(0, 1) }}</i>
        <span><b>{{ participant.displayName }}</b><small>{{ participant.status }}</small></span>
        <em class="participant-role">{{ participant.isHost ? '호스트' : participant.role }}</em>
        <button
          v-if="isHost && !participant.isHost && participant.participantId"
          class="transfer-host-button"
          type="button"
          :disabled="Boolean(transferringHostId)"
          @click="pendingHostTransfer = participant"
        >
          {{ transferringHostId === participant.id ? '위임 중…' : '호스트 위임' }}
        </button>
      </article>
    </div>
    <section v-if="pendingHostTransfer" class="host-transfer-confirm">
      <div>
        <b>{{ pendingHostTransfer.displayName }} 님에게 호스트를 위임할까요?</b>
        <small>위임 후에는 해당 참가자가 회의 종료와 호스트 권한을 갖습니다.</small>
      </div>
      <footer>
        <button type="button" :disabled="Boolean(transferringHostId)" @click="pendingHostTransfer = null">
          취소
        </button>
        <button
          class="confirm-transfer-button"
          type="button"
          :disabled="Boolean(transferringHostId)"
          @click="confirmTransferHost"
        >
          {{ transferringHostId ? '위임 중…' : '위임하기' }}
        </button>
      </footer>
    </section>
  </BaseModal>
  <BaseModal
    v-if="modal === 'device-setup'"
    modal-class="meeting-info-modal device-setup-modal"
    @close="skipEntryDeviceSetup"
  >
    <small>DEVICE SETUP</small>
    <h2>카메라와 마이크를 켤까요?</h2>
    <p class="device-setup-description">
      회의에 입장했습니다. 사용할 장치를 선택하면 브라우저 권한 요청이 표시됩니다.
    </p>
    <div class="device-setup-options">
      <button
        type="button"
        :class="{ selected: entryDeviceSelection.microphone }"
        @click="entryDeviceSelection.microphone = !entryDeviceSelection.microphone"
      >
        <i>🎙</i>
        <span><b>마이크</b><small>회의에서 내 음성을 전달합니다.</small></span>
        <em>{{ entryDeviceSelection.microphone ? '켜기' : '끄기' }}</em>
      </button>
      <button
        type="button"
        :class="{ selected: entryDeviceSelection.camera }"
        @click="entryDeviceSelection.camera = !entryDeviceSelection.camera"
      >
        <i>▣</i>
        <span><b>카메라</b><small>회의에서 내 영상을 공유합니다.</small></span>
        <em>{{ entryDeviceSelection.camera ? '켜기' : '끄기' }}</em>
      </button>
    </div>
    <footer class="device-setup-actions">
      <button type="button" :disabled="deviceSetupPending" @click="skipEntryDeviceSetup">
        끄고 참여
      </button>
      <button
        class="enable-entry-devices"
        type="button"
        :disabled="deviceSetupPending || !hasSelectedEntryDevice"
        @click="enableSelectedEntryDevices"
      >
        {{ deviceSetupPending ? '장치 연결 중…' : '선택한 장치 켜기' }}
      </button>
    </footer>
  </BaseModal>
  <BaseModal v-if="modal === 'help'" modal-class="meeting-info-modal" @close="modal = ''">
    <small>MEETING HELP</small>
    <h2>회의 도움말</h2>
    <div class="meeting-help-content">
      <article v-for="item in helpItems" :key="item.title">
        <b>{{ item.title }}</b><p>{{ item.description }}</p>
      </article>
    </div>
  </BaseModal>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../../user/composables/useUserPage'
import { meetingControls } from '../constants/meetingControls'
import { useBoardPage } from '../composables/useBoardPage'
import { useLiveKitMeeting } from '../composables/useLiveKitMeeting'
import { useVadRecording } from '../composables/useVadRecording'
import { boardStore } from '../stores/boardStore'

const { boardState, meetingId } = useBoardPage({
  resources: ['meetingRoom']
})
const router = useRouter()
const { userState } = useUserPage()
const { notify } = useToast()
const participantRefreshTimers = []
const {
  room: liveKitRoom,
  status: liveKitStatus,
  participantIdentities,
  screenShareIdentities,
  activeSpeakerIdentities,
  deviceState,
  connect: connectLiveKit,
  mountParticipantMedia,
  setDeviceEnabled,
  requestServerExit,
  disconnectAfterServerExit
} = useLiveKitMeeting({
  onStatusChange: status => boardStore.setMeetingConnectionStatus(status),
  onDisconnected: ({ message }) => {
    notify(message)
    boardStore.clearMeetingRoom()
    const teamId = boardState.activeMeeting.teamId || boardState.currentTeamId
    router.replace(teamId ? `/teams/${teamId}/schedule` : '/home')
  }
})
const {
  status: vadStatus,
  pendingUploads: vadPendingUploads,
  start: startVadRecording,
  stopMonitoring: stopVadRecording,
  stopAndDrain: drainVadUploads
} = useVadRecording({
  onUploadSuccess: ({ sequence }) => {
    notify(`VAD 청크 #${sequence} 업로드 완료`)
  },
  onUploadError: error => {
    notify(error?.message || 'VAD 업로드에 실패했습니다.')
  }
})
let vadStartTimer = 0
const liveParticipants = computed(() => {
  const participants = boardState.meetingRoom.participants
  if (!participantIdentities.value.length) {
    return participants.filter(participant => participant.isInMeeting)
  }

  const byIdentity = new Map(
    participants.map(participant => [participant.livekitIdentity, participant])
  )
  return participantIdentities.value.map(identity => {
    const participant = byIdentity.get(identity) || {
      id: identity,
      livekitIdentity: identity,
      displayName: identity === liveKitRoom.value?.localParticipant?.identity
        ? userState.profile?.nickname || '나'
        : '참가자',
      avatarText: identity === liveKitRoom.value?.localParticipant?.identity
        ? (userState.profile?.nickname || '나').slice(0, 2)
        : '?',
      muted: false,
      isHost: false,
      status: '회의 참여 중'
    }
    return { ...participant, mediaKey: identity }
  })
})
const participantsPerPage = 4
const currentVideoPage = ref(0)
const liveParticipantCount = computed(() => liveParticipants.value.length)
const meetingTiles = computed(() => {
  const participants = liveParticipants.value
  const screenTiles = screenShareIdentities.value.map(identity => {
    const owner = participants.find(participant => participant.livekitIdentity === identity)
    const displayName = owner?.displayName || '참가자'
    return {
      id: `screen:${identity}`,
      mediaKey: `screen:${identity}`,
      livekitIdentity: identity,
      displayName: `${displayName} 화면 공유`,
      avatarText: '공유',
      muted: true,
      isScreenShare: true
    }
  })
  const tiles = [...screenTiles, ...participants]
  if (!pinnedParticipantId.value) return tiles
  return [...tiles].sort((left, right) => {
    if (left.id === pinnedParticipantId.value) return -1
    if (right.id === pinnedParticipantId.value) return 1
    return 0
  })
})
const videoPageCount = computed(() =>
  Math.max(1, Math.ceil(meetingTiles.value.length / participantsPerPage))
)
const visibleParticipants = computed(() => {
  const pageStart = currentVideoPage.value * participantsPerPage
  return meetingTiles.value.slice(pageStart, pageStart + participantsPerPage)
})
const videoLayoutClass = computed(() => {
  const count = visibleParticipants.value.length
  if (pinnedParticipantId.value && count > 1) return 'layout-pinned'
  if (count <= 1) return 'layout-single'
  if (count === 2) return 'layout-two'
  if (count === 3) return 'layout-three'
  return 'layout-four'
})
const participantPreview = computed(() => liveParticipants.value.slice(0, 3))
const isHost = computed(() => Boolean(boardState.meetingRoom.connection?.isHost))
const connectionStatusLabel = computed(() => ({
  connecting: '연결 중',
  connected: '연결됨',
  reconnecting: '재연결 중',
  error: '연결 실패',
  disconnected: '연결 종료'
}[liveKitStatus.value] || '대기 중'))
const activeTab = ref('chat')
const activeContactId = ref('')
const activeContact = computed(() =>
  boardState.meetingRoom.directContacts.find(item => item.id === activeContactId.value)
)
const pinnedParticipantId = ref('')
const modal = ref('')
const draft = ref('')
const exiting = ref(false)
const transferringHostId = ref('')
const pendingHostTransfer = ref(null)
const deviceSetupPending = ref(false)
const hasAskedDeviceSetup = ref(false)
const entryDeviceSelection = reactive({ microphone: true, camera: true })
const hasSelectedEntryDevice = computed(() =>
  entryDeviceSelection.microphone || entryDeviceSelection.camera
)
const messageList = ref(null)
const meetingRoomElement = ref(null)
const showMore = ref(false)
const isFullscreen = ref(false)
const isChatPanelOpen = ref(true)
const activeControls = reactive(new Set(['chat']))
const messagePlaceholder = computed(() => {
  if (activeTab.value === 'chat') return '메시지를 입력하세요...'
  if (!activeContact.value) return '대화 상대를 선택하세요...'
  return `${activeContact.value.name}에게 메시지 보내기...`
})
const helpItems = [
  { title: '마이크와 카메라', description: '하단 버튼을 눌러 마이크와 카메라를 켜거나 끌 수 있습니다.' },
  { title: '화면 고정', description: '참가자 영상에 마우스를 올린 뒤 핀 버튼을 누르면 화면을 고정할 수 있습니다.' },
  { title: '채팅 패널', description: '채팅 버튼을 눌러 오른쪽 패널을 열고 닫을 수 있습니다.' },
  { title: '화면 공유', description: '공유 버튼을 누르면 화면이나 특정 창을 팀원에게 공유할 수 있습니다.' }
]

const scheduleParticipantRefresh = () => {
  for (const delay of [500, 1500]) {
    participantRefreshTimers.push(window.setTimeout(() => {
      boardStore.loadMeetingParticipants(meetingId.value).catch(() => undefined)
    }, delay))
  }
}

watch(
  () => boardState.meetingRoom.connection?.token,
  async token => {
    if (!token) return
    try {
      await connectLiveKit(boardState.meetingRoom.connection)
      scheduleParticipantRefresh()
      if (
        !hasAskedDeviceSetup.value &&
        (!deviceState.microphone || !deviceState.camera)
      ) {
        hasAskedDeviceSetup.value = true
        entryDeviceSelection.microphone = !deviceState.microphone
        entryDeviceSelection.camera = !deviceState.camera
        modal.value = 'device-setup'
      }
    } catch (error) {
      notify(error?.message || 'LiveKit 화상회의에 연결하지 못했습니다.')
    }
  },
  { immediate: true }
)

watch(participantIdentities, identities => {
  boardState.meetingRoom.totalParticipants = Math.max(
    boardState.meetingRoom.participants.length,
    identities.length
  )
})

watch(screenShareIdentities, (identities, previousIdentities = []) => {
  if (identities.length > previousIdentities.length) {
    currentVideoPage.value = 0
  }
})

watch(
  [
    () => deviceState.microphone,
    () => deviceState.camera,
    () => deviceState.screen
  ],
  ([microphone, camera, screen]) => {
    const states = { mic: microphone, camera, share: screen }
    for (const [control, enabled] of Object.entries(states)) {
      if (enabled) activeControls.add(control)
      else activeControls.delete(control)
    }
  }
)

function scheduleVadStart() {
  window.clearTimeout(vadStartTimer)
  if (!deviceState.microphone || !liveKitRoom.value || !meetingId.value) {
    stopVadRecording()
    return
  }

  vadStartTimer = window.setTimeout(() => {
    if (!deviceState.microphone || !liveKitRoom.value || !meetingId.value) return
    const started = startVadRecording(meetingId.value, liveKitRoom.value)
    if (!started) {
      console.warn('[VAD] microphone track is not ready yet')
    }
  }, 400)
}

watch(
  () => [deviceState.microphone, liveKitRoom.value, meetingId.value],
  () => scheduleVadStart(),
  { immediate: true }
)

watch(videoPageCount, pageCount => {
  currentVideoPage.value = Math.min(
    currentVideoPage.value,
    pageCount - 1
  )
})

watch(meetingTiles, tiles => {
  if (pinnedParticipantId.value && !tiles.some(tile => tile.id === pinnedParticipantId.value)) {
    pinnedParticipantId.value = ''
  }
})

const syncFullscreenState = () => {
  isFullscreen.value = document.fullscreenElement === meetingRoomElement.value
}

onMounted(() => {
  document.addEventListener('fullscreenchange', syncFullscreenState)
})

onBeforeUnmount(() => {
  participantRefreshTimers.forEach(timer => window.clearTimeout(timer))
  window.clearTimeout(vadStartTimer)
  stopVadRecording()
  document.removeEventListener('fullscreenchange', syncFullscreenState)
})

function selectTab(tab) {
  activeTab.value = tab
  if (tab === 'chat') activeContactId.value = ''
}

function togglePin(participant) {
  const willPin = pinnedParticipantId.value !== participant.id
  pinnedParticipantId.value = willPin ? participant.id : ''
  currentVideoPage.value = 0
  notify(willPin ? `${participant.displayName} 화면을 고정했습니다.` : '화면 고정을 해제했습니다.')
}

async function toggleParticipantMic(participant) {
  if (participant.livekitIdentity !== liveKitRoom.value?.localParticipant?.identity) {
    notify('다른 참가자의 마이크는 프론트에서 직접 제어할 수 없습니다.')
    return
  }
  try {
    const enabled = !deviceState.microphone
    await setDeviceEnabled('microphone', enabled)
    if (enabled) activeControls.add('mic')
    else activeControls.delete('mic')
    notify(enabled ? '마이크를 켰습니다.' : '마이크를 음소거했습니다.')
  } catch (error) {
    notify(error?.message || '마이크 상태를 변경하지 못했습니다.')
  }
}

async function handleControl(id) {
  if (id === 'people') {
    modal.value = 'participants'
    return
  }
  if (id === 'more') {
    showMore.value = !showMore.value
    if (showMore.value) activeControls.add(id)
    else activeControls.delete(id)
    return
  }
  if (id === 'document') {
    notify('공유문서와 연결됩니다.')
    return
  }
  if (id === 'chat') {
    isChatPanelOpen.value = !isChatPanelOpen.value
    if (isChatPanelOpen.value) activeControls.add('chat')
    else activeControls.delete('chat')
    return
  }
  const deviceByControl = {
    mic: 'microphone',
    camera: 'camera',
    share: 'screen'
  }
  const device = deviceByControl[id]
  if (device) {
    try {
      const enabled = !deviceState[device]
      await setDeviceEnabled(device, enabled)
      if (enabled) activeControls.add(id)
      else activeControls.delete(id)
      const control = meetingControls.find(item => item.id === id)
      notify(`${control?.label || '장치'}를 ${enabled ? '켰습니다.' : '껐습니다.'}`)
    } catch (error) {
      notify(error?.message || '장치 상태를 변경하지 못했습니다.')
    }
    return
  }
}

async function exitMeeting() {
  if (exiting.value) return
  if (isHost.value && !window.confirm('회의를 종료하면 모든 참가자의 연결이 종료됩니다. 계속할까요?')) {
    return
  }

  const action = isHost.value ? 'end' : 'leave'
  exiting.value = true
  requestServerExit(action)
  try {
    await drainVadUploads()
    if (isHost.value) await boardStore.endMeeting(meetingId.value)
    else await boardStore.leaveMeeting(meetingId.value)
    await disconnectAfterServerExit(action)
  } catch (error) {
    requestServerExit('')
    notify(error?.message || '회의 연결을 종료하지 못했습니다.')
  } finally {
    exiting.value = false
  }
}

function closeParticipantsModal() {
  if (transferringHostId.value) return
  pendingHostTransfer.value = null
  modal.value = ''
}

function skipEntryDeviceSetup() {
  if (deviceSetupPending.value) return
  modal.value = ''
}

async function enableSelectedEntryDevices() {
  if (deviceSetupPending.value || !hasSelectedEntryDevice.value) return
  deviceSetupPending.value = true
  try {
    if (entryDeviceSelection.microphone && !deviceState.microphone) {
      await setDeviceEnabled('microphone', true)
      activeControls.add('mic')
    }
    if (entryDeviceSelection.camera && !deviceState.camera) {
      await setDeviceEnabled('camera', true)
      activeControls.add('camera')
    }
    modal.value = ''
    notify('선택한 회의 장치를 켰습니다.')
  } catch (error) {
    notify(error?.message || '장치를 켜지 못했습니다. 브라우저 권한을 확인해주세요.')
  } finally {
    deviceSetupPending.value = false
  }
}

async function confirmTransferHost() {
  const participant = pendingHostTransfer.value
  if (!participant?.participantId || transferringHostId.value) return
  transferringHostId.value = participant.id
  try {
    await boardStore.transferMeetingHost(
      meetingId.value,
      participant.participantId
    )
    notify(`${participant.displayName} 님에게 호스트를 위임했습니다.`)
    pendingHostTransfer.value = null
  } catch (error) {
    notify(error?.message || '호스트를 위임하지 못했습니다.')
  } finally {
    transferringHostId.value = ''
  }
}

async function toggleFullscreen() {
  try {
    if (document.fullscreenElement) {
      await document.exitFullscreen()
    } else if (meetingRoomElement.value?.requestFullscreen) {
      await meetingRoomElement.value.requestFullscreen()
    } else {
      throw new Error('이 브라우저에서는 전체 화면을 사용할 수 없습니다.')
    }
  } catch (error) {
    notify(error?.message || '전체 화면을 전환하지 못했습니다.')
  } finally {
    showMore.value = false
    activeControls.delete('more')
  }
}

async function send() {
  const body = draft.value.trim()
  if (!body) return
  try {
    const payload = {
      sender: userState.profile?.nickname || '나',
      body
    }
    if (activeTab.value === 'direct' && activeContact.value) {
      await boardStore.sendDirectMessage(
        meetingId.value,
        activeContact.value.id,
        payload
      )
    } else {
      await boardStore.sendMessage(meetingId.value, payload)
    }
    draft.value = ''
    await nextTick()
    if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
  } catch (error) {
    notify(error?.message || '메시지를 보내지 못했습니다.')
  }
}
</script>

<style scoped>
.meeting-room,
.meeting-room :where(button, input, textarea, select, label, small, b, strong, em),
:deep(.meeting-info-modal),
:deep(.meeting-info-modal :where(button, input, textarea, select, label, small, b, strong, em)) {
  font-family: 'Noto Sans KR', sans-serif;
}

.meeting-room {
  font-weight: 400;
  letter-spacing: -0.01em;
}

.meeting-content.chat-closed {
  grid-template-columns: minmax(0, 1fr) !important;
}

.connection-status {
  margin-left: 8px;
  color: #b9c4d8;
  font-size: 11px;
}

.vad-status {
  margin-left: 8px;
  color: #7dd3a0;
  font-size: 11px;
}

.connection-status.connected {
  color: #66dfac;
}

.connection-status.reconnecting,
.connection-status.connecting {
  color: #ffd166;
}

.connection-status.error,
.connection-status.disconnected {
  color: #ff8c8c;
}

.video-grid {
  position: relative;
  min-width: 0;
  min-height: 0;
  padding: 12px;
  border: 1.5px solid rgba(222, 232, 250, 0.82);
  border-radius: 20px;
  background:
    radial-gradient(circle at 50% 0%, rgba(68, 104, 170, 0.2), transparent 52%),
    rgba(6, 24, 53, 0.48);
  box-shadow:
    inset 0 0 0 1px rgba(255, 255, 255, 0.04),
    0 12px 32px rgba(3, 14, 35, 0.2);
  overflow: hidden;
}

.video-grid.layout-single {
  grid-template-columns: minmax(0, 1fr) !important;
  grid-template-rows: minmax(0, 1fr) !important;
}

.video-grid.layout-two {
  grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  grid-template-rows: minmax(0, 1fr) !important;
}

.video-grid.layout-three {
  grid-template-columns: repeat(3, minmax(0, 1fr)) !important;
  grid-template-rows: minmax(0, 1fr) !important;
}

.video-grid.layout-four {
  grid-template-columns: repeat(2, minmax(0, 1fr)) !important;
  grid-template-rows: repeat(2, minmax(0, 1fr)) !important;
}

.video-grid.layout-pinned {
  grid-template-columns: minmax(0, 3fr) minmax(180px, 1fr) !important;
  grid-template-rows: repeat(3, minmax(0, 1fr)) !important;
}

.video-grid.layout-pinned .video-tile.is-pinned {
  grid-column: 1 !important;
  grid-row: 1 / -1 !important;
}

.video-grid.layout-pinned .video-tile:not(.is-pinned) {
  grid-column: 2 !important;
}

.video-tile {
  min-width: 0;
  min-height: 0;
  border: 1px solid rgba(221, 231, 248, 0.42);
  box-shadow: 0 8px 24px rgba(2, 11, 29, 0.24);
}

.video-tile.is-speaking {
  box-shadow: 0 0 0 3px #5ce2a4;
}

.video-tile.is-pinned {
  border-color: #8aa0ff;
  box-shadow: 0 0 0 2px rgba(116, 140, 255, 0.72);
}

.video-tile.is-screen-share {
  background: #07162f;
}

.video-pagination {
  position: absolute;
  left: 50%;
  bottom: 18px;
  z-index: 6;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 8px;
  border: 1px solid rgba(226, 235, 250, 0.48);
  border-radius: 999px;
  background: rgba(7, 20, 43, 0.82);
  color: #fff;
  box-shadow: 0 8px 20px rgba(1, 8, 22, 0.28);
  transform: translateX(-50%);
  backdrop-filter: blur(8px);
}

.video-pagination button {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  padding: 0;
  border: 0;
  border-radius: 50%;
  background: #fff;
  color: #172d52;
  font-size: 22px;
  line-height: 1;
}

.video-pagination button:disabled {
  cursor: default;
  opacity: 0.35;
}

.video-pagination span {
  min-width: 34px;
  text-align: center;
  font-size: 11px;
  font-weight: 700;
}

.participant-media {
  position: absolute;
  inset: 0;
  z-index: 0;
  overflow: hidden;
}

.participant-media :deep(.livekit-video) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.video-tile.is-screen-share .participant-media :deep(.livekit-video) {
  object-fit: contain;
}

.participant-media :deep(.livekit-audio) {
  display: none;
}

.meeting-pin,
.participant-badge {
  z-index: 1;
}

.screen-share-mark {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #6e84f5;
  color: #fff;
  font-size: 12px;
  font-style: normal;
}

:deep(.meeting-info-modal) {
  width: min(520px, calc(100vw - 32px));
}

.meeting-member-list {
  max-height: min(52vh, 420px);
  overflow-y: auto;
}

.meeting-member-row {
  display: grid !important;
  grid-template-columns: 38px minmax(0, 1fr) auto auto;
  gap: 10px !important;
  min-height: 56px;
  padding: 10px 12px !important;
  border: 1px solid transparent;
}

.meeting-member-row.is-host {
  border-color: #cad5ff;
  background: #eef2ff !important;
}

.participant-role {
  margin-left: 0 !important;
  padding: 4px 7px;
  border-radius: 999px;
  background: #e6eaf3;
  color: #596476 !important;
  font-size: 10px !important;
  font-weight: 700;
  white-space: nowrap;
}

.meeting-member-row.is-host .participant-role {
  background: #d9e1ff;
  color: #4059d6 !important;
}

.transfer-host-button {
  min-width: 86px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #6178e8;
  border-radius: 8px;
  background: #fff;
  color: #4059d6;
  font-size: 10px;
  font-weight: 700;
}

.transfer-host-button:hover:not(:disabled) {
  background: #edf1ff;
}

.transfer-host-button:disabled,
.host-transfer-confirm button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.host-transfer-confirm {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #cfd8ff;
  border-radius: 12px;
  background: #f5f7ff;
}

.host-transfer-confirm > div {
  display: grid;
  gap: 5px;
}

.host-transfer-confirm small {
  color: #657086;
  line-height: 1.5;
}

.host-transfer-confirm footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.host-transfer-confirm button {
  height: 34px;
  padding: 0 16px;
  border: 1px solid #d5d9e4;
  border-radius: 8px;
  background: #fff;
  color: #566173;
  font-size: 11px;
  font-weight: 700;
}

.host-transfer-confirm .confirm-transfer-button {
  border-color: #536be0;
  background: #536be0;
  color: #fff;
}

.device-setup-description {
  margin: -12px 0 18px;
  color: #657086;
  font-size: 11px;
  line-height: 1.6;
}

.device-setup-options {
  display: grid;
  gap: 9px;
}

.device-setup-options > button {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-height: 64px;
  padding: 10px 12px;
  border: 1px solid #d8deeb;
  border-radius: 11px;
  background: #f8f9fc;
  text-align: left;
}

.device-setup-options > button.selected {
  border-color: #7188ff;
  background: #eef2ff;
}

.device-setup-options i {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #e5eaff;
  color: #4059d6;
  font-size: 18px;
  font-style: normal;
}

.device-setup-options span {
  display: grid;
  gap: 3px;
}

.device-setup-options span small {
  color: #6d778a;
}

.device-setup-options em {
  min-width: 42px;
  color: #8a93a3;
  font-size: 10px;
  font-style: normal;
  font-weight: 700;
  text-align: center;
}

.device-setup-options .selected em {
  color: #4059d6;
}

.device-setup-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}

.device-setup-actions button {
  height: 38px;
  padding: 0 16px;
  border: 1px solid #d5d9e4;
  border-radius: 8px;
  background: #fff;
  color: #566173;
  font-size: 11px;
  font-weight: 700;
}

.device-setup-actions .enable-entry-devices {
  border-color: #536be0;
  background: #536be0;
  color: #fff;
}

.device-setup-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.meeting-room:fullscreen {
  width: 100vw;
  height: 100vh;
}

.meeting-more-menu > button > i img {
  display: block;
  width: 16px;
  height: 16px;
}

</style>
