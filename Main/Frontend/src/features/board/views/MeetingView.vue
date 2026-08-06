<template>
  <main ref="meetingRoomElement" class="meeting-room dark">
    <header class="meeting-top">
      <div class="meeting-title-block">
        <h1>{{ boardState.activeMeeting.roomTitle || '회의' }}</h1>
        <small class="connection-status" :class="liveKitStatus">
          {{ connectionStatusLabel }}
        </small>
        <small v-if="vadStatus === 'recording'" class="vad-status">
          VAD {{ vadPendingUploads > 0 ? `업로드 ${vadPendingUploads}` : '감지 중' }}
        </small>
      </div>
      <div class="meeting-top-actions">
        <button class="participant-count" type="button" @click="toggleSidePanel('participants')">
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
    <div class="meeting-content" :class="{ 'chat-closed': !sidePanel }">
      <section class="video-column" :class="{ 'controls-collapsed': controlsCollapsed && !controlsHovered }">
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
            <div
              v-if="participant.cameraOff && !participant.isScreenShare"
              class="participant-avatar"
            >
              <img
                v-if="avatarImageUrl(participant)"
                :src="avatarImageUrl(participant)"
                :alt="`${participant.displayName} 프로필 이미지`"
                @error="markAvatarImageFailed(participant.profileImageUrl)"
              />
              <i v-else>{{ (participant.avatarText || '?').slice(0, 1) }}</i>
            </div>
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
                class="badge-device"
                :class="{ 'is-off': participant.muted }"
                type="button"
                :aria-label="`${participant.displayName} 마이크 ${participant.muted ? '켜기' : '음소거'}`"
                @click="toggleParticipantDevice(participant, 'microphone')"
              >
                <i class="material-symbols-rounded" aria-hidden="true">
                  {{ participant.muted ? 'mic_off' : 'mic' }}
                </i>
              </button>
              <i v-else class="screen-share-mark">↗</i>
              <button
                v-if="!participant.isScreenShare"
                class="badge-device"
                :class="{ 'is-off': participant.cameraOff }"
                type="button"
                :aria-label="`${participant.displayName} 카메라 ${participant.cameraOff ? '켜기' : '끄기'}`"
                @click="toggleParticipantDevice(participant, 'camera')"
              >
                <i class="material-symbols-rounded" aria-hidden="true">
                  {{ participant.cameraOff ? 'videocam_off' : 'videocam' }}
                </i>
              </button>
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
        <footer
          class="controls"
          :class="{ collapsed: controlsCollapsed && !controlsHovered }"
          @mouseleave="controlsHovered = false"
        >
          <button
            class="controls-collapse-handle"
            :class="{ 'is-collapsed': controlsCollapsed }"
            type="button"
            :aria-label="controlsCollapsed ? '회의 제어 펼치기' : '회의 제어 숨기기'"
            :aria-expanded="!controlsCollapsed"
            @mouseenter="controlsHovered = controlsCollapsed"
            @click="toggleControlsCollapsed"
          >
            <span aria-hidden="true"></span>
          </button>
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
            <button type="button" @click="openDeviceSettings">
              <i class="material-symbols-rounded" aria-hidden="true">settings_input_component</i>
              <span><b>장치 설정</b><small>마이크·카메라·출력 장치 변경</small></span>
              <em>›</em>
            </button>
          </section>
        </footer>
      </section>

      <aside v-if="sidePanel" class="chat-panel" :class="{ 'participants-panel': sidePanel === 'participants' }">
        <template v-if="sidePanel === 'participants'">
          <header class="side-panel-header">
            <div><small>PARTICIPANTS</small><h2>참가자 ({{ liveParticipantCount }})</h2></div>
            <button type="button" aria-label="참가자 패널 닫기" @click="toggleSidePanel('participants')">×</button>
          </header>
          <section v-if="isHost" class="meeting-invite-section">
            <button class="meeting-invite-trigger" type="button" :aria-expanded="showInvitePanel" @click="toggleInvitePanel">
              <span aria-hidden="true">♙+</span> 초대 및 알림
            </button>
          </section>
          <div v-if="showInvitePanel" class="meeting-invite-overlay" @click.self="toggleInvitePanel">
            <section class="meeting-invite-dialog" role="dialog" aria-modal="true" aria-labelledby="meeting-invite-title">
              <header>
                <div>
                  <small>TEAM MEMBERS</small>
                  <h3 id="meeting-invite-title">멤버 및 초대</h3>
                </div>
                <button type="button" aria-label="초대 창 닫기" @click="toggleInvitePanel">×</button>
              </header>
              <form @submit.prevent="searchMeetingInviteCandidates">
                <input v-model="meetingInviteKeyword" type="search" placeholder="이름 또는 이메일 검색" />
                <button type="submit" :disabled="loadingInviteCandidates">
                  {{ loadingInviteCandidates ? '검색 중…' : '검색' }}
                </button>
              </form>
              <p class="meeting-invite-guide">
                {{ meetingInviteKeyword.trim() ? '검색 결과' : '초대 가능한 팀 스페이스 멤버 전체' }}
                <b>{{ boardState.meetingInviteCandidates.length }}명</b>
              </p>
              <div v-if="boardState.meetingInviteCandidates.length" class="meeting-invite-results">
                <article v-for="candidate in boardState.meetingInviteCandidates" :key="candidate.id">
                  <i>{{ candidate.avatarText.slice(0, 1) }}</i>
                  <span><b>{{ candidate.name }}</b><small>{{ candidate.email }}</small></span>
                  <button type="button" :disabled="invitingUserId === candidate.userId" @click="inviteMeetingCandidate(candidate)">
                    {{ invitingUserId === candidate.userId ? '초대 중…' : '초대' }}
                  </button>
                </article>
              </div>
              <p v-else-if="hasSearchedInviteCandidates" class="meeting-invite-empty">
                초대할 수 있는 팀원이 없습니다.
              </p>
            </section>
          </div>
          <header class="participant-list-heading">
            <b>⌄　참가자 ({{ liveParticipantCount }})</b>
          </header>
          <section class="side-participant-list">
            <article v-for="participant in liveParticipants" :key="participant.id" :class="{ 'is-host': participant.isHost }">
              <i>{{ participant.avatarText.slice(0, 1) }}</i>
              <span><b>{{ participant.displayName }}</b><small>{{ participant.isHost ? '호스트' : participant.status }}</small></span>
              <span
                class="member-device-state"
                :aria-label="`마이크 ${participant.muted ? '꺼짐' : '켜짐'}, 카메라 ${participant.cameraOff ? '꺼짐' : '켜짐'}`"
              >
                <button
                  class="device-mark"
                  :class="{ 'is-off': participant.muted }"
                  type="button"
                  :title="`마이크 ${participant.muted ? '꺼짐' : '켜짐'}`"
                  @click="toggleParticipantDevice(participant, 'microphone')"
                >
                  <i class="material-symbols-rounded" aria-hidden="true">
                    {{ participant.muted ? 'mic_off' : 'mic' }}
                  </i>
                </button>
                <button
                  class="device-mark"
                  :class="{ 'is-off': participant.cameraOff }"
                  type="button"
                  :title="`카메라 ${participant.cameraOff ? '꺼짐' : '켜짐'}`"
                  @click="toggleParticipantDevice(participant, 'camera')"
                >
                  <i class="material-symbols-rounded" aria-hidden="true">
                    {{ participant.cameraOff ? 'videocam_off' : 'videocam' }}
                  </i>
                </button>
              </span>
              <div v-if="isHost && !participant.isHost && participant.participantId" class="side-participant-actions">
                <button type="button" @click="pendingHostTransfer = participant">위임</button>
                <button type="button" class="danger" @click="kickMeetingParticipant(participant)">강퇴</button>
              </div>
            </article>
          </section>
          <section v-if="pendingHostTransfer" class="host-transfer-confirm side-transfer-confirm">
            <div><b>{{ pendingHostTransfer.displayName }} 님에게 호스트를 위임할까요?</b><small>회의 종료 권한도 함께 이동합니다.</small></div>
            <footer>
              <button type="button" @click="pendingHostTransfer = null">취소</button>
              <button class="confirm-transfer-button" type="button" @click="confirmTransferHost">위임하기</button>
            </footer>
          </section>
        </template>
        <template v-else>
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
            <div v-if="!boardState.meetingRoom.chatMessages.length" class="chat-empty-state">
              <i aria-hidden="true">💬</i>
              <b>아직 대화가 없어요</b>
              <small>첫 메시지를 보내 회의를 시작해 보세요.</small>
            </div>
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
        </template>
      </aside>
    </div>
  </main>

  <BaseModal
    v-if="modal === 'device-setup' || modal === 'device-settings'"
    modal-class="meeting-info-modal device-setup-modal"
    @close="closeDeviceSetup"
  >
    <small>DEVICE SETUP</small>
    <h2>{{ modal === 'device-setup' ? '카메라와 마이크를 켤까요?' : '회의 장치 설정' }}</h2>
    <p class="device-setup-description">
      {{ modal === 'device-setup'
        ? '회의에 입장했습니다. 사용할 장치를 선택하면 브라우저 권한 요청이 표시됩니다.'
        : '회의 연결을 유지한 상태로 사용할 장치를 변경합니다.' }}
    </p>
    <div class="device-setup-options">
      <button
        type="button"
        :class="{ selected: entryDeviceSelection.microphone }"
        @click="entryDeviceSelection.microphone = !entryDeviceSelection.microphone"
      >
        <i class="material-symbols-rounded" aria-hidden="true">mic</i>
        <span><b>마이크</b><small>회의에서 내 음성을 전달합니다.</small></span>
        <em>{{ entryDeviceSelection.microphone ? '켜기' : '끄기' }}</em>
      </button>
      <button
        type="button"
        :class="{ selected: entryDeviceSelection.camera }"
        @click="entryDeviceSelection.camera = !entryDeviceSelection.camera"
      >
        <i class="material-symbols-rounded" aria-hidden="true">videocam</i>
        <span><b>카메라</b><small>회의에서 내 영상을 공유합니다.</small></span>
        <em>{{ entryDeviceSelection.camera ? '켜기' : '끄기' }}</em>
      </button>
    </div>
    <div class="device-selectors">
      <label>
        <span><i class="material-symbols-rounded" aria-hidden="true">mic</i>마이크 장치</span>
        <select
          :value="selectedDeviceIds.audioinput"
          :disabled="!availableDevices.audioinput.length"
          @change="selectMeetingDevice('audioinput', $event.target.value)"
        >
          <option v-for="(device, index) in availableDevices.audioinput" :key="device.deviceId" :value="device.deviceId">
            {{ device.label || `마이크 ${index + 1}` }}
          </option>
        </select>
      </label>
      <label>
        <span><i class="material-symbols-rounded" aria-hidden="true">videocam</i>카메라 장치</span>
        <select
          :value="selectedDeviceIds.videoinput"
          :disabled="!availableDevices.videoinput.length"
          @change="selectMeetingDevice('videoinput', $event.target.value)"
        >
          <option v-for="(device, index) in availableDevices.videoinput" :key="device.deviceId" :value="device.deviceId">
            {{ device.label || `카메라 ${index + 1}` }}
          </option>
        </select>
      </label>
      <label v-if="supportsAudioOutputSelection && availableDevices.audiooutput.length">
        <span><i class="material-symbols-rounded" aria-hidden="true">speaker</i>출력 장치</span>
        <select
          :value="selectedDeviceIds.audiooutput"
          @change="selectMeetingDevice('audiooutput', $event.target.value)"
        >
          <option v-for="(device, index) in availableDevices.audiooutput" :key="device.deviceId" :value="device.deviceId">
            {{ device.label || `스피커 ${index + 1}` }}
          </option>
        </select>
      </label>
      <p v-else class="audio-output-notice">이 브라우저에서는 별도 출력 장치 선택을 지원하지 않습니다.</p>
    </div>
    <footer class="device-setup-actions">
      <button type="button" :disabled="deviceSetupPending" @click="closeDeviceSetup">
        {{ modal === 'device-setup' ? '끄고 참여' : '취소' }}
      </button>
      <button
        class="enable-entry-devices"
        type="button"
        :disabled="deviceSetupPending || (modal === 'device-setup' && !hasSelectedEntryDevice)"
        @click="enableSelectedEntryDevices"
      >
        {{ deviceSetupPending
          ? '장치 연결 중…'
          : modal === 'device-setup' ? '선택한 장치 켜기' : '장치 적용' }}
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

<script>
export default {
  name: 'MeetingView'
}
</script>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../../user/composables/useUserPage'
import { meetingControls } from '../constants/meetingControls'
import { useBoardPage } from '../composables/useBoardPage'
import { useLiveKitMeeting } from '../composables/useLiveKitMeeting'
import {
  createMeetingPictureInPictureVideo,
  findMeetingPictureInPictureVideo,
  meetingDocumentMode,
  releaseMeetingPictureInPictureVideo
} from '../composables/useMeetingDocumentMode'
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
  micMutedIdentities,
  cameraOffIdentities,
  deviceState,
  availableDevices,
  selectedDeviceIds,
  connect: connectLiveKit,
  loadMediaDevices,
  selectDevice,
  applySelectedDevices,
  mountParticipantMedia,
  sendChatMessage,
  setDeviceEnabled,
  requestServerExit,
  disconnectAfterServerExit
} = useLiveKitMeeting({
  preserveConnectionOnUnmount: () => meetingDocumentMode.isActiveMeeting(meetingId.value),
  onStatusChange: status => boardStore.setMeetingConnectionStatus(status),
  onDisconnected: ({ message }) => {
    meetingDocumentMode.clear()
    notify(message)
    boardStore.clearMeetingRoom()
    const teamId = boardState.activeMeeting.teamId || boardState.currentTeamId
    router.replace(teamId ? `/teams/${teamId}/schedule` : '/home')
  },
  onChatMessage: payload => {
    boardStore.appendChatMessage(toChatMessageViewModel(payload, false))
    scrollChatToBottom()
  }
})
const {
  status: vadStatus,
  pendingUploads: vadPendingUploads,
  start: startVadRecording,
  stopMonitoring: stopVadRecording,
  stopAndDrain: drainVadUploads
} = useVadRecording({
  uploadRecording: (activeMeetingId, recording) =>
    boardStore.uploadVadRecording(activeMeetingId, recording),
  onUploadSuccess: ({ sequence }) => {
    notify(`VAD 청크 #${sequence} 업로드 완료`)
  },
  onUploadError: error => {
    notify(error?.message || 'VAD 업로드에 실패했습니다.')
  }
})
let vadStartTimer = 0

function createChatMessageId() {
  return (
    globalThis.crypto?.randomUUID?.() ||
    `chat-${Date.now()}-${Math.random().toString(36).slice(2)}`
  )
}

function toChatMessageViewModel(payload, mine) {
  const sentAt = payload.sentAt ? new Date(payload.sentAt) : new Date()
  const isValidTime = !Number.isNaN(sentAt.getTime())
  return {
    id: createChatMessageId(),
    sender: payload.sender || '참가자',
    time: (isValidTime ? sentAt : new Date()).toLocaleTimeString('ko-KR', {
      hour: '2-digit',
      minute: '2-digit'
    }),
    body: payload.body,
    mine
  }
}

async function scrollChatToBottom() {
  await nextTick()
  if (messageList.value) messageList.value.scrollTop = messageList.value.scrollHeight
}
const liveParticipants = computed(() => {
  const participants = boardState.meetingRoom.participants
  if (!participantIdentities.value.length) {
    return participants
      .filter(participant => participant.isInMeeting)
      .map(participant => ({ ...participant, muted: true, cameraOff: true }))
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
      isHost: false,
      status: '회의 참여 중',
      role: '참여자',
      participantRole: ''
    }
    return {
      ...participant,
      muted: micMutedIdentities.value.includes(identity),
      cameraOff: cameraOffIdentities.value.includes(identity),
      mediaKey: identity
    }
  })
})
const failedAvatarImageUrls = reactive(new Set())
const avatarImageUrl = participant => {
  const url = participant.profileImageUrl
  if (!url || failedAvatarImageUrls.has(url)) return ''
  return url
}
const markAvatarImageFailed = url => {
  if (url) failedAvatarImageUrls.add(url)
}

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
const kickingParticipantId = ref('')
const meetingInviteKeyword = ref('')
const loadingInviteCandidates = ref(false)
const hasSearchedInviteCandidates = ref(false)
const invitingUserId = ref(null)
const showInvitePanel = ref(false)
const deviceSetupPending = ref(false)
const hasAskedDeviceSetup = ref(false)
const entryDeviceSelection = reactive({ microphone: true, camera: true })
const hasSelectedEntryDevice = computed(() =>
  entryDeviceSelection.microphone || entryDeviceSelection.camera
)
const supportsAudioOutputSelection = 'setSinkId' in HTMLMediaElement.prototype
const messageList = ref(null)
const meetingRoomElement = ref(null)
const showMore = ref(false)
const isFullscreen = ref(false)
const sidePanel = ref('participants')
const controlsCollapsed = ref(false)
const controlsHovered = ref(false)
let pictureInPictureVideo = null
let suppressPictureInPictureReturn = false

const toggleControlsCollapsed = () => {
  controlsCollapsed.value = !controlsCollapsed.value
  controlsHovered.value = false
}

const activeControls = reactive(new Set(['people']))

async function toggleSidePanel(panel) {
  const nextPanel = sidePanel.value === panel ? '' : panel
  sidePanel.value = nextPanel
  activeControls.delete('people')
  activeControls.delete('chat')
  if (nextPanel) activeControls.add(nextPanel === 'participants' ? 'people' : 'chat')

  if (nextPanel !== 'participants') showInvitePanel.value = false
}
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
        await loadMediaDevices()
        modal.value = 'device-setup'
      }
    } catch (error) {
      notify(error?.message || 'LiveKit 화상회의에 연결하지 못했습니다.')
    }
  },
  { immediate: true }
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
    if (!started) console.warn('[VAD] microphone track is not ready yet')
  }, 400)
}

watch(
  () => [deviceState.microphone, liveKitRoom.value, meetingId.value],
  scheduleVadStart,
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
  const shouldKeepPictureInPicture = meetingDocumentMode.isActiveMeeting(meetingId.value)
  if (!shouldKeepPictureInPicture) {
    pictureInPictureVideo?.removeEventListener(
      'leavepictureinpicture',
      returnToMeetingFromPictureInPicture
    )
    pictureInPictureVideo = null
    releaseMeetingPictureInPictureVideo()
  }
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

const deviceLabels = { microphone: '마이크', camera: '카메라' }
const controlIdByDevice = { microphone: 'mic', camera: 'camera' }

async function toggleParticipantDevice(participant, device) {
  const label = deviceLabels[device] || '장치'
  if (participant.livekitIdentity !== liveKitRoom.value?.localParticipant?.identity) {
    notify(`다른 참가자의 ${label}는 프론트에서 직접 제어할 수 없습니다.`)
    return
  }
  try {
    const enabled = !deviceState[device]
    await setDeviceEnabled(device, enabled)
    const controlId = controlIdByDevice[device]
    if (enabled) activeControls.add(controlId)
    else activeControls.delete(controlId)
    notify(`${label}를 ${enabled ? '켰습니다.' : '껐습니다.'}`)
  } catch (error) {
    notify(error?.message || `${label} 상태를 변경하지 못했습니다.`)
  }
}

async function handleControl(id) {
  if (id === 'people') {
    await toggleSidePanel('participants')
    return
  }
  if (id === 'more') {
    showMore.value = !showMore.value
    if (showMore.value) activeControls.add(id)
    else activeControls.delete(id)
    return
  }
  if (id === 'document') {
    await openDocumentsInPictureInPicture()
    return
  }
  if (id === 'chat') {
    await toggleSidePanel('chat')
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

async function returnToMeetingFromPictureInPicture() {
  if (suppressPictureInPictureReturn) return
  if (!meetingDocumentMode.isActiveMeeting(meetingId.value)) return

  const returnRoute = meetingDocumentMode.state.returnRoute || `/meetings/${meetingId.value}`
  try {
    await router.push(returnRoute)
  } finally {
    meetingDocumentMode.clear()
    releaseMeetingPictureInPictureVideo()
    pictureInPictureVideo = null
  }
}

async function openDocumentsInPictureInPicture() {
  if (!document.pictureInPictureEnabled) {
    notify('현재 브라우저에서는 PiP 모드를 지원하지 않습니다.')
    return
  }

  const teamId = boardState.activeMeeting.teamId || boardState.currentTeamId
  if (!teamId) {
    notify('회의가 연결된 팀 스페이스를 찾을 수 없습니다.')
    return
  }

  const sourceVideo = findMeetingPictureInPictureVideo(meetingRoomElement.value)

  const returnRoute = router.currentRoute.value.fullPath || `/meetings/${meetingId.value}`
  meetingDocumentMode.begin({
    meetingId: meetingId.value,
    teamId,
    returnRoute
  })

  try {
    if (document.pictureInPictureElement) {
      suppressPictureInPictureReturn = true
      await document.exitPictureInPicture()
      suppressPictureInPictureReturn = false
    }

    const video = await createMeetingPictureInPictureVideo(sourceVideo)
    if (!video?.requestPictureInPicture) {
      throw new Error('현재 브라우저에서는 PiP 모드를 지원하지 않습니다.')
    }

    pictureInPictureVideo = video
    video.addEventListener(
      'leavepictureinpicture',
      returnToMeetingFromPictureInPicture,
      { once: true }
    )

    await video.requestPictureInPicture()
    await router.push({
      name: 'team-documents',
      params: { teamId: String(teamId) },
      query: { meetingId: meetingId.value }
    })
  } catch (error) {
    pictureInPictureVideo?.removeEventListener(
      'leavepictureinpicture',
      returnToMeetingFromPictureInPicture
    )
    suppressPictureInPictureReturn = true
    if (document.pictureInPictureElement === pictureInPictureVideo) {
      await document.exitPictureInPicture().catch(() => undefined)
    }
    suppressPictureInPictureReturn = false
    releaseMeetingPictureInPictureVideo()
    pictureInPictureVideo = null
    meetingDocumentMode.clear()
    notify(error?.message || '공유 문서 PiP 모드를 시작하지 못했습니다.')
  }
}

async function exitMeeting() {
  if (exiting.value) return
  if (isHost.value && !window.confirm('회의를 종료하면 모든 참가자의 연결이 종료됩니다. 계속할까요?')) {
    return
  }

  const action = isHost.value ? 'end' : 'leave'
  meetingDocumentMode.clear()
  releaseMeetingPictureInPictureVideo()
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

async function searchMeetingInviteCandidates() {
  if (!isHost.value || loadingInviteCandidates.value) return
  loadingInviteCandidates.value = true
  try {
    await boardStore.loadMeetingInviteCandidates(
      meetingId.value,
      meetingInviteKeyword.value
    )
    hasSearchedInviteCandidates.value = true
  } catch (error) {
    notify(error?.message || '초대 가능한 팀원을 조회하지 못했습니다.')
  } finally {
    loadingInviteCandidates.value = false
  }
}

async function toggleInvitePanel() {
  showInvitePanel.value = !showInvitePanel.value
  if (showInvitePanel.value) {
    meetingInviteKeyword.value = ''
    hasSearchedInviteCandidates.value = false
    await searchMeetingInviteCandidates()
  }
}

async function inviteMeetingCandidate(candidate) {
  if (!candidate?.userId || invitingUserId.value) return
  invitingUserId.value = candidate.userId
  try {
    await boardStore.inviteMeetingMember(meetingId.value, candidate.userId)
    notify(`${candidate.name} 님을 회의에 초대했습니다.`)
  } catch (error) {
    notify(error?.message || '회의 초대에 실패했습니다.')
  } finally {
    invitingUserId.value = null
  }
}

async function kickMeetingParticipant(participant) {
  if (!participant?.participantId || kickingParticipantId.value) return
  if (!window.confirm(`${participant.displayName} 님을 회의에서 강퇴할까요?`)) return

  kickingParticipantId.value = participant.id
  try {
    await boardStore.kickMeetingParticipant(
      meetingId.value,
      participant.participantId
    )
    notify(`${participant.displayName} 님을 회의에서 강퇴했습니다.`)
  } catch (error) {
    notify(error?.message || '참가자를 강퇴하지 못했습니다.')
  } finally {
    kickingParticipantId.value = ''
  }
}

function closeDeviceSetup() {
  if (deviceSetupPending.value) return
  modal.value = ''
}

function selectMeetingDevice(kind, deviceId) {
  selectDevice(kind, deviceId)
}

async function openDeviceSettings() {
  showMore.value = false
  activeControls.delete('more')
  try {
    await loadMediaDevices()
    entryDeviceSelection.microphone = deviceState.microphone
    entryDeviceSelection.camera = deviceState.camera
    modal.value = 'device-settings'
  } catch (error) {
    notify(error?.message || '사용 가능한 장치를 불러오지 못했습니다.')
  }
}

async function enableSelectedEntryDevices() {
  if (
    deviceSetupPending.value ||
    (modal.value === 'device-setup' && !hasSelectedEntryDevice.value)
  ) return
  const isInMeetingSettings = modal.value === 'device-settings'
  deviceSetupPending.value = true
  try {
    await applySelectedDevices()
    if (isInMeetingSettings) {
      if (entryDeviceSelection.microphone !== deviceState.microphone) {
        await setDeviceEnabled('microphone', entryDeviceSelection.microphone)
      }
      if (entryDeviceSelection.camera !== deviceState.camera) {
        await setDeviceEnabled('camera', entryDeviceSelection.camera)
      }
    } else {
      if (entryDeviceSelection.microphone && !deviceState.microphone) {
        await setDeviceEnabled('microphone', true)
      }
      if (entryDeviceSelection.camera && !deviceState.camera) {
        await setDeviceEnabled('camera', true)
      }
    }
    if (entryDeviceSelection.microphone) activeControls.add('mic')
    else activeControls.delete('mic')
    if (entryDeviceSelection.camera) activeControls.add('camera')
    else activeControls.delete('camera')
    modal.value = ''
    notify(isInMeetingSettings ? '회의 장치를 변경했습니다.' : '선택한 회의 장치를 켰습니다.')
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
    if (activeTab.value === 'direct' && activeContact.value) {
      await boardStore.sendDirectMessage(
        meetingId.value,
        activeContact.value.id,
        { sender: userState.profile?.nickname || '나', body }
      )
    } else {
      const sent = await sendChatMessage(body)
      boardStore.appendChatMessage(toChatMessageViewModel(sent, true))
    }
    draft.value = ''
    await scrollChatToBottom()
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
  position: relative;
  isolation: isolate;
  font-weight: 400;
  letter-spacing: -0.01em;
  --meeting-surface: rgba(27, 28, 32, 0.9);
  --meeting-surface-strong: rgba(20, 21, 25, 0.96);
  --meeting-border: rgba(255, 255, 255, 0.045);
  --meeting-muted: #a7a9b2;
  background:
    radial-gradient(circle at 48% -20%, rgba(255, 255, 255, 0.055), transparent 42%),
    linear-gradient(145deg, #18191d 0%, #0d0e11 100%) !important;
}

.meeting-top,
.meeting-content {
  position: relative;
  z-index: 1;
}

.meeting-top {
  height: 58px !important;
  padding: 0 20px 0 24px !important;
  border-bottom: 1px solid rgba(255, 255, 255, 0.055);
  border-radius: 0 !important;
  background: rgba(17, 18, 21, 0.88) !important;
  box-shadow: 0 8px 28px rgba(0, 0, 0, 0.16);
  backdrop-filter: blur(18px);
}

.meeting-title-block {
  display: flex;
  align-items: center;
  gap: 10px;
}

.meeting-title-block h1 {
  margin: 0;
  color: #fff;
  font-size: 17px !important;
  font-weight: 800;
  letter-spacing: -0.025em;
}

.meeting-top-actions {
  gap: 10px !important;
}

.meeting-top-actions > button {
  height: 38px !important;
  border-color: rgba(255, 255, 255, 0.055) !important;
  border-radius: 12px !important;
  background: rgba(255, 255, 255, 0.06) !important;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.06);
}

.meeting-top-actions > button:hover {
  border-color: rgba(255, 255, 255, 0.1) !important;
  background: rgba(255, 255, 255, 0.075) !important;
}

.meeting-content {
  height: calc(100vh - 58px) !important;
  grid-template-columns: minmax(0, 1fr) minmax(292px, 326px) !important;
  gap: 14px !important;
  padding: 14px !important;
}

.meeting-content.chat-closed {
  grid-template-columns: minmax(0, 1fr) !important;
}

.connection-status {
  display: inline-flex;
  align-items: center;
  min-height: 23px;
  margin-left: 0;
  padding: 0 9px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.06);
  color: #b9c4d8;
  font-size: 9px;
  font-weight: 700;
}

.vad-status {
  margin-left: 8px;
  color: #7dd3a0;
  font-size: 11px;
  font-weight: 700;
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
  padding: 14px;
  border: 1px solid var(--meeting-border);
  border-radius: 22px;
  background:
    radial-gradient(circle at 50% -10%, rgba(255, 255, 255, 0.045), transparent 48%),
    linear-gradient(145deg, rgba(27, 28, 32, 0.92), rgba(13, 14, 17, 0.88));
  box-shadow: 0 16px 38px rgba(0, 0, 0, 0.2);
  overflow: hidden;
}

.video-grid.layout-single {
  grid-template-columns: minmax(0, 1fr) !important;
  grid-template-rows: minmax(0, 1fr) !important;
  place-items: center;
}

.video-grid.layout-single .video-tile {
  width: min(100%, 1120px);
  height: min(100%, 680px);
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
  border: 1px solid rgba(255, 255, 255, 0.035);
  border-radius: 18px !important;
  background: linear-gradient(145deg, #303136, #1b1c21) !important;
  box-shadow: 0 12px 30px rgba(2, 10, 27, 0.28);
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.video-tile:hover {
  border-color: rgba(255, 255, 255, 0.09);
}

.video-tile.is-speaking {
  border-color: #64e2aa;
  box-shadow: 0 0 0 2px rgba(100, 226, 170, 0.68), 0 14px 32px rgba(2, 10, 27, 0.3);
}

.video-tile.is-pinned {
  border-color: rgba(255, 255, 255, 0.34);
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.16), 0 14px 32px rgba(0, 0, 0, 0.32);
}

.video-tile.is-screen-share {
  background: #111216 !important;
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
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 999px;
  background: rgba(22, 23, 27, 0.9);
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
  background: #303137;
  color: #f0f0f2;
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

.participant-badge button .material-symbols-rounded {
  font-size: 15px;
  line-height: 1;
}

.participant-badge.is-muted button {
  color: inherit;
}

.participant-badge button.badge-device.is-off {
  color: #ff6674;
}

.participant-media :deep(.livekit-video.is-mirrored) {
  transform: scaleX(-1);
}

.participant-avatar {
  position: absolute;
  inset: 0;
  z-index: 2;
  display: grid;
  place-items: center;
  background: #000;
}

.participant-avatar img,
.participant-avatar i {
  width: 33%;
  min-width: 48px;
  max-width: 96px;
  aspect-ratio: 1;
  border-radius: 50%;
}

.participant-avatar img {
  object-fit: cover;
}

.participant-avatar i {
  display: grid;
  place-items: center;
  background: #1b2436;
  color: rgba(255, 255, 255, 0.92);
  font-style: normal;
  font-weight: 600;
  font-size: clamp(18px, 3vw, 34px);
}

.device-mark {
  display: grid;
  place-items: center;
  padding: 0;
  border: 0;
  background: transparent;
  color: #d9dade;
  cursor: pointer;
}

.device-mark .material-symbols-rounded {
  font-size: 18px;
  line-height: 1;
}

.device-mark.is-off {
  color: #e39a9a;
}

.member-device-state {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.participant-media :deep(.livekit-audio) {
  display: none;
}

.meeting-pin,
.participant-badge {
  z-index: 3;
}

.screen-share-mark {
  display: grid;
  place-items: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #3b3c42;
  color: #fff;
  font-size: 12px;
  font-style: normal;
}

.participant-badge {
  left: 14px !important;
  bottom: 14px !important;
  min-height: 34px;
  padding: 5px 11px 5px 7px !important;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px !important;
  background: rgba(17, 18, 21, 0.76) !important;
  box-shadow: 0 7px 18px rgba(0, 0, 0, 0.18);
  backdrop-filter: blur(10px);
}

.controls {
  min-width: 0;
  padding: 6px 16px !important;
  gap: 12px !important;
  border: 1px solid var(--meeting-border) !important;
  border-radius: 18px !important;
  background: var(--meeting-surface) !important;
  box-shadow: 0 14px 32px rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(18px);
  transition: min-height 180ms ease, transform 180ms ease, opacity 180ms ease;
}

.video-column {
  transition: grid-template-rows 180ms ease;
}

.video-column.controls-collapsed {
  grid-template-rows: minmax(0, 1fr) 14px !important;
  gap: 4px;
}

.controls-collapse-handle {
  position: absolute;
  top: -20px;
  left: 50%;
  z-index: 5;
  display: grid !important;
  place-items: center;
  width: 76px !important;
  height: 20px !important;
  padding: 0 !important;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.055) !important;
  border-bottom: 0 !important;
  border-radius: 9px 9px 0 0 !important;
  background: var(--meeting-surface) !important;
  color: #aeb0b8 !important;
  box-shadow: none;
  transform: translateX(-50%);
}

.controls-collapse-handle::after {
  position: absolute;
  right: -1px;
  bottom: -2px;
  left: -1px;
  height: 3px;
  background: #1b1c20;
  content: '';
}

.controls-collapse-handle:hover {
  color: #fff !important;
  background: #25262b !important;
}

.controls-collapse-handle span {
  position: relative;
  z-index: 1;
  display: block;
  width: 16px;
  height: 16px;
}

.controls-collapse-handle span::before {
  position: absolute;
  inset: 0;
  background: currentColor;
  clip-path: polygon(20% 2%, 80% 2%, 70% 34%, 100% 55%, 58% 55%, 52% 100%, 42% 55%, 0 55%, 30% 34%);
  content: '';
  transform: rotate(45deg) scale(0.88);
  transition: transform 160ms ease;
}

.controls-collapse-handle span::after {
  position: absolute;
  top: 7px;
  left: -1px;
  width: 18px;
  height: 1.5px;
  border-radius: 999px;
  background: currentColor;
  content: '';
  transform: rotate(-45deg);
  transition: opacity 160ms ease;
}

.controls-collapse-handle.is-collapsed span::before {
  transform: rotate(0deg) scale(0.88);
}

.controls-collapse-handle.is-collapsed span::after {
  opacity: 0;
}

.controls.collapsed {
  min-height: 14px;
  padding: 0 !important;
  border-color: transparent !important;
  background: rgba(20, 21, 24, 0.58) !important;
  box-shadow: none;
}

.controls.collapsed > :not(.controls-collapse-handle) {
  visibility: hidden;
  opacity: 0;
  pointer-events: none;
}

.meeting-agenda {
  min-width: 0;
  max-width: 210px;
  overflow: hidden;
  color: #aab9d5 !important;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.control-items {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.controls .meeting-control {
  width: 44px !important;
  height: 54px !important;
  border-radius: 11px !important;
  transition: transform 160ms ease, background 160ms ease !important;
}

.controls .meeting-control:hover {
  background: rgba(255, 255, 255, 0.045) !important;
  transform: translateY(-2px);
}

.controls .meeting-control > span {
  border-color: rgba(255, 255, 255, 0.055) !important;
  background: #292a2f !important;
  box-shadow: 0 5px 12px rgba(0, 0, 0, 0.16);
}

.controls .meeting-control > span img {
  filter: brightness(0) invert(0.82) !important;
}

.controls .meeting-control small {
  color: #8f9199 !important;
}

.controls .meeting-control.active > span,
.controls .meeting-control.primary > span {
  border-color: rgba(255, 255, 255, 0.12) !important;
  background: #3a3b41 !important;
}

.controls .meeting-control.active > span img,
.controls .meeting-control.primary > span img {
  filter: brightness(0) invert(1) !important;
}

.controls .meeting-control.active small,
.controls .meeting-control.primary small {
  color: #e2e3e7 !important;
}

.controls .hangup {
  width: 72px !important;
  border-radius: 12px !important;
  background: #a92f35 !important;
  box-shadow: 0 8px 18px rgba(93, 12, 16, 0.22);
  transition: transform 160ms ease, filter 160ms ease;
}

.controls .hangup:hover:not(:disabled) {
  filter: brightness(1.08);
  transform: translateY(-2px);
}

.chat-panel {
  position: relative;
  grid-template-rows: 62px minmax(0, 1fr) 94px !important;
  border: 1px solid var(--meeting-border) !important;
  border-radius: 20px !important;
  background: var(--meeting-surface-strong) !important;
  box-shadow: 0 18px 38px rgba(0, 0, 0, 0.22);
  backdrop-filter: blur(18px);
}

.participants-panel {
  grid-template-rows: auto auto auto minmax(0, 1fr) auto !important;
  overflow: hidden;
}

.side-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 18px 12px;
}

.side-panel-header div {
  display: grid;
  gap: 3px;
}

.side-panel-header small {
  color: #858892;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.side-panel-header h2 {
  margin: 0;
  color: #f5f5f6;
  font-size: 18px;
}

.side-panel-header > button {
  display: grid;
  width: 34px;
  height: 34px;
  place-items: center;
  border: 0;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  color: #d9dade;
  font-size: 22px;
}

.participants-panel .meeting-invite-section {
  margin: 0 14px 12px;
  padding: 0;
  border: 0;
  background: transparent;
}

.meeting-invite-trigger {
  justify-self: start;
  height: 36px;
  padding: 0 14px;
  border: 1px solid rgba(255, 255, 255, 0.42);
  border-radius: 999px;
  background: transparent;
  color: #f0f0f2;
  font-size: 12px;
  font-weight: 700;
}

.meeting-invite-trigger:hover,
.meeting-invite-trigger[aria-expanded='true'] {
  border-color: rgba(255, 255, 255, 0.7);
  background: rgba(255, 255, 255, 0.07);
}

.meeting-invite-trigger span {
  margin-right: 5px;
  font-size: 15px;
}

.meeting-invite-overlay {
  position: absolute;
  inset: 0;
  z-index: 30;
  display: grid;
  align-items: start;
  padding: 12px;
  border-radius: inherit;
  background: rgba(12, 13, 16, 0.76);
  backdrop-filter: blur(8px);
}

.meeting-invite-dialog {
  display: grid;
  grid-template-rows: auto auto auto minmax(0, 1fr);
  gap: 12px;
  max-height: calc(100% - 24px);
  padding: 16px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16px;
  background: #24252a;
  box-shadow: 0 22px 50px rgba(0, 0, 0, 0.38);
}

.meeting-invite-dialog > header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.meeting-invite-dialog > header div {
  display: grid;
  gap: 2px;
}

.meeting-invite-dialog > header small {
  color: #858892;
  font-size: 8px;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.meeting-invite-dialog > header h3 {
  margin: 0;
  color: #f5f5f6;
  font-size: 17px;
}

.meeting-invite-dialog > header button {
  display: grid;
  width: 32px;
  height: 32px;
  place-items: center;
  border: 0;
  border-radius: 9px;
  background: rgba(255, 255, 255, 0.06);
  color: #d9dade;
  font-size: 20px;
}

.meeting-invite-dialog form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.meeting-invite-dialog input {
  min-width: 0;
  height: 40px;
  padding: 0 13px;
  border-color: rgba(255, 255, 255, 0.08);
  border-style: solid;
  border-width: 1px;
  border-radius: 11px;
  outline: none;
  background: rgba(255, 255, 255, 0.08);
  color: #fff;
}

.meeting-invite-dialog input:focus {
  border-color: rgba(255, 255, 255, 0.28);
}

.meeting-invite-dialog input::placeholder {
  color: #858892;
}

.meeting-invite-dialog form button,
.meeting-invite-dialog .meeting-invite-results button {
  height: 40px;
  padding: 0 14px;
  border-color: #4a4b51;
  border-style: solid;
  border-width: 1px;
  border-radius: 10px;
  background: #3a3b40;
  color: #fff;
  font-weight: 700;
}

.meeting-invite-guide {
  display: flex;
  justify-content: space-between;
  margin: 0;
  color: #a9abb2;
  font-size: 10px;
}

.meeting-invite-guide b {
  color: #f1f1f3;
}

.meeting-invite-dialog .meeting-invite-results {
  max-height: none;
  min-height: 0;
}

.meeting-invite-dialog .meeting-invite-results article {
  background: rgba(255, 255, 255, 0.055);
}

.meeting-invite-dialog .meeting-invite-results span b {
  color: #f2f2f4;
}

.participant-list-heading {
  padding: 4px 18px 8px;
  color: #e8e8eb;
  font-size: 14px;
}

.side-participant-list {
  display: grid;
  align-content: start;
  gap: 6px;
  min-height: 0;
  padding: 4px 14px 16px;
  overflow-y: auto;
}

.side-participant-list > article {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.035);
}

.side-participant-list > article > i {
  display: grid;
  width: 42px;
  height: 42px;
  place-items: center;
  border-radius: 50%;
  background: #3a3b40;
  color: #fff;
  font-style: normal;
  font-weight: 800;
}

.side-participant-list > article > span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.side-participant-list > article > span b {
  overflow: hidden;
  color: #f1f1f3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.side-participant-list > article > span small {
  color: #92949c;
}

.side-participant-actions {
  display: flex;
  gap: 4px;
}

.side-participant-actions button {
  height: 28px;
  padding: 0 8px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.06);
  color: #d9dade;
  font-size: 9px;
}

.side-participant-actions button.danger {
  color: #e39a9a;
}

.side-transfer-confirm {
  margin: 0 14px 14px;
  border-color: rgba(255, 255, 255, 0.08);
  background: #24252a;
}

.side-transfer-confirm b {
  color: #f0f0f2;
}

.chat-tabs {
  gap: 6px;
  margin: 10px;
  padding: 4px;
  border: 0 !important;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.24);
}

.chat-tabs button {
  border: 0 !important;
  border-radius: 9px;
  background: transparent !important;
  color: #9eafd0 !important;
  font-size: 11px;
  font-weight: 700;
  transition: color 160ms ease, background 160ms ease;
}

.chat-tabs button.active {
  border: 0 !important;
  background: rgba(255, 255, 255, 0.09) !important;
  color: #fff !important;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.055);
}

.messages {
  padding: 18px 16px !important;
}

.messages article b {
  color: #e9efff;
}

.messages p {
  line-height: 1.5;
  box-shadow: 0 6px 16px rgba(2, 10, 27, 0.12);
}

.chat-empty-state {
  display: grid;
  place-items: center;
  align-content: center;
  gap: 7px;
  min-height: 100%;
  color: #d8e1f5;
  text-align: center;
}

.chat-empty-state i {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  margin-bottom: 5px;
  border: 1px solid rgba(147, 169, 216, 0.18);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.055);
  font-size: 20px;
  font-style: normal;
}

.chat-empty-state b {
  font-size: 12px;
}

.chat-empty-state small {
  color: #92949c;
  font-size: 9px;
}

.meeting-pin {
  background: #34353a !important;
  color: #f2f2f4 !important;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.28) !important;
}

.meeting-pin:hover {
  background: #45464c !important;
}

.meeting-pin.is-pinned {
  background: #202126 !important;
  box-shadow: 0 0 0 2px rgba(255, 255, 255, 0.35), 0 7px 16px rgba(0, 0, 0, 0.34) !important;
}

.direct-contact i {
  background: #3a3b40 !important;
  color: #fff;
}

.direct-conversation article.me p,
.messages article.me p {
  background: #3a3b40 !important;
  color: #fff !important;
}

.chat-input {
  align-self: center;
  min-height: 54px;
  margin: 12px !important;
  padding: 8px 9px 8px 14px !important;
  border: 1px solid rgba(255, 255, 255, 0.04) !important;
  border-radius: 14px !important;
  background: rgba(255, 255, 255, 0.96) !important;
  box-shadow: 0 10px 24px rgba(1, 8, 22, 0.2);
}

.chat-input button {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px !important;
  background: #303137 !important;
  color: #fff !important;
}

.chat-input button:disabled {
  background: #e2e3e6 !important;
  color: #9a9ca3 !important;
}

@media (max-width: 1080px) {
  .meeting-content {
    grid-template-columns: minmax(0, 1fr) minmax(260px, 292px) !important;
    padding: 10px !important;
  }

  .meeting-agenda {
    display: none;
  }
}

@media (max-width: 820px) {
  .meeting-content {
    grid-template-columns: minmax(0, 1fr) !important;
  }

  .chat-panel {
    position: absolute;
    inset: 78px 10px 10px auto;
    z-index: 20;
    width: min(360px, calc(100vw - 20px));
  }

  .meeting-help b,
  .participant-count > b {
    display: none;
  }
}

:deep(.meeting-info-modal) {
  width: min(520px, calc(100vw - 32px));
}

:deep(.meeting-info-modal > small) {
  color: #55565d !important;
}

.meeting-invite-section {
  display: grid;
  gap: 10px;
  margin: 16px 0;
  padding: 14px;
  border: 1px solid #dedfe3;
  border-radius: 14px;
  background: #f6f6f7;
}

.meeting-invite-section form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
}

.meeting-invite-section input {
  min-width: 0;
  height: 40px;
  padding: 0 13px;
  border: 1px solid #d4d5da;
  border-radius: 10px;
  background: #fff;
  color: #202126;
}

.meeting-invite-section form button,
.meeting-invite-results button {
  height: 40px;
  padding: 0 15px;
  border: 1px solid #38393f;
  border-radius: 10px;
  background: #38393f;
  color: #fff;
  font-weight: 700;
}

.meeting-invite-section button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.meeting-invite-results {
  display: grid;
  gap: 6px;
  max-height: 190px;
  overflow-y: auto;
}

.meeting-invite-results article {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 10px;
  background: #fff;
}

.meeting-invite-results i {
  display: grid;
  width: 36px;
  height: 36px;
  place-items: center;
  border-radius: 10px;
  background: #e6e7ea;
  color: #33343a;
  font-style: normal;
  font-weight: 800;
}

.meeting-invite-results span {
  display: grid;
  min-width: 0;
}

.meeting-invite-results small {
  overflow: hidden;
  color: #727987;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meeting-invite-results button {
  height: 34px;
}

.meeting-invite-empty {
  margin: 0;
  color: #777d88;
  font-size: 12px;
  text-align: center;
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
  border-color: #d4d5d9;
  background: #f2f2f3 !important;
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
  background: #dfe0e3;
  color: #35363b !important;
}

.participant-actions {
  display: flex;
  gap: 6px;
}

.transfer-host-button {
  min-width: 86px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid #55565d;
  border-radius: 8px;
  background: #fff;
  color: #35363b;
  font-size: 10px;
  font-weight: 700;
}

.transfer-host-button:hover:not(:disabled) {
  background: #ededee;
}

.kick-participant-button {
  height: 32px;
  padding: 0 11px;
  border: 1px solid #d3b7b7;
  border-radius: 8px;
  background: #fff;
  color: #a04444;
  font-size: 10px;
  font-weight: 700;
}

.kick-participant-button:hover:not(:disabled) {
  background: #f8eeee;
}

.transfer-host-button:disabled,
.kick-participant-button:disabled,
.host-transfer-confirm button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.host-transfer-confirm {
  display: grid;
  gap: 14px;
  margin-top: 16px;
  padding: 16px;
  border: 1px solid #d5d6da;
  border-radius: 12px;
  background: #f5f5f6;
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
  border-color: #303137;
  background: #303137;
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
  border-color: #55565d;
  background: #eeeeef;
}

.meeting-member-row > i {
  background: #3a3b40 !important;
  color: #fff !important;
}

.device-setup-options i {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  border-radius: 10px;
  background: #dedfe2;
  color: #35363b;
  font-size: 18px;
  font-style: normal;
  font-variation-settings: 'FILL' 0, 'wght' 500, 'GRAD' 0, 'opsz' 24;
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
  color: #292a2f;
}

.device-selectors {
  display: grid;
  gap: 10px;
  margin-top: 14px;
  padding-top: 14px;
  border-top: 1px solid #e2e4e9;
}

.device-selectors label {
  display: grid;
  grid-template-columns: 120px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}

.device-selectors label > span {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #41434a;
  font-size: 11px;
  font-weight: 700;
}

.device-selectors label > span i {
  color: #64666e;
  font-size: 18px;
  font-style: normal;
  font-variation-settings: 'FILL' 0, 'wght' 500, 'GRAD' 0, 'opsz' 20;
}

.device-selectors select {
  width: 100%;
  min-width: 0;
  height: 40px;
  padding: 0 34px 0 13px;
  border: 1px solid #d5d9e4;
  border-radius: 11px;
  outline: none;
  background: #f8f9fb;
  color: #292b31;
  font: inherit;
  font-size: 11px;
}

.device-selectors select:focus {
  border-color: #55565d;
  box-shadow: 0 0 0 3px rgba(48, 49, 55, 0.08);
}

.device-selectors select:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.audio-output-notice {
  margin: 0;
  padding: 10px 12px;
  border-radius: 10px;
  background: #f3f4f6;
  color: #747b89;
  font-size: 10px;
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
  border-color: #303137;
  background: #303137;
  color: #fff;
}

.device-setup-actions button:disabled {
  cursor: wait;
  opacity: 0.55;
}

.meeting-more-menu {
  border-color: #d7d7da !important;
  color: #24252a !important;
  box-shadow: 0 18px 38px rgba(0, 0, 0, 0.2) !important;
}

.meeting-more-menu > button {
  color: #303137 !important;
}

.meeting-more-menu > button:hover,
.meeting-more-menu > button.enabled {
  background: #f0f0f1 !important;
}

.meeting-more-menu > button > i {
  background: #dedfe2 !important;
  color: #35363b !important;
}

.meeting-more-menu > button > em {
  color: #35363b !important;
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
