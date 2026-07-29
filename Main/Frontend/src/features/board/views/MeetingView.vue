<template>
  <main class="meeting-room dark">
    <header class="meeting-top">
      <h1>🔴　{{ boardState.activeMeeting.roomTitle }} ({{ boardState.activeMeeting.status }})</h1>
      <div class="meeting-top-actions">
        <button class="participant-count" type="button" @click="modal = 'participants'">
          <span>
            <i v-for="participant in participantPreview" :key="participant.id">
              {{ participant.avatarText.slice(0, 1) }}
            </i>
          </span>
          <b>{{ boardState.meetingRoom.totalParticipants }}명 참가 중</b>
        </button>
        <button class="meeting-help" type="button" @click="modal = 'help'">
          <i>?</i><b>도움말</b>
        </button>
      </div>
    </header>
    <div class="meeting-content">
      <section class="video-column">
        <div class="video-grid">
          <article
            v-for="participant in visibleParticipants"
            :key="participant.id"
            class="video-tile"
          >
            <div class="video-person">{{ participant.avatarText }}</div>
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
                type="button"
                :aria-label="`${participant.displayName} 마이크 ${participant.muted ? '켜기' : '음소거'}`"
                @click="toggleParticipantMic(participant)"
              >
                {{ participant.muted ? '🔇' : '🎙' }}
              </button>
              <span>{{ participant.displayName }}</span>
            </div>
          </article>
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
          <button class="hangup" type="button" aria-label="통화 종료" @click="$router.push('/home')">
            <img src="/assets/icons/hangup.svg" alt="" aria-hidden="true" />
          </button>

          <section v-if="showMore" class="meeting-more-menu">
            <header><b>더보기</b><small>회의 기능 설정</small></header>
            <button
              type="button"
              :class="{ enabled: moreOptions.blur }"
              @click="toggleMoreOption('blur', '배경 흐리기')"
            >
              <i>◫</i><span><b>배경 흐리기</b><small>내 영상의 배경을 흐리게 표시</small></span><em>✓</em>
            </button>
            <button
              type="button"
              :class="{ enabled: moreOptions.fullscreen }"
              @click="toggleMoreOption('fullscreen', '전체 화면')"
            >
              <i>⛶</i><span><b>전체 화면</b><small>회의 화면을 크게 보기</small></span><em>✓</em>
            </button>
            <hr />
            <button type="button" @click="notify('회의 설정 화면을 준비했습니다.')">
              <i><img src="/assets/icons/settings.svg?v=20260728-1301" alt="" aria-hidden="true" /></i>
              <span><b>회의 설정</b><small>오디오와 비디오 장치 설정</small></span><em>→</em>
            </button>
          </section>
        </footer>
      </section>

      <aside class="chat-panel">
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

  <BaseModal v-if="modal === 'participants'" modal-class="meeting-info-modal" @close="modal = ''">
    <small>MEETING PARTICIPANTS</small>
    <h2>참가자 {{ boardState.meetingRoom.totalParticipants }}명</h2>
    <div class="meeting-member-list">
      <article v-for="participant in boardState.meetingRoom.participants" :key="participant.id">
        <i>{{ participant.avatarText.slice(0, 1) }}</i>
        <span><b>{{ participant.displayName }}</b><small>{{ participant.status }}</small></span>
        <em>{{ participant.role }}</em>
      </article>
      <p v-if="otherParticipantCount > 0">외 {{ otherParticipantCount }}명이 참여하고 있습니다.</p>
    </div>
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
import { computed, nextTick, reactive, ref } from 'vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../../user/composables/useUserPage'
import { meetingControls } from '../constants/meetingControls'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const { boardState, meetingId } = useBoardPage({
  resources: ['activeMeeting', 'meetingRoom']
})
const { userState } = useUserPage()
const { notify } = useToast()
const visibleParticipants = computed(() => boardState.meetingRoom.participants.slice(0, 4))
const participantPreview = computed(() => boardState.meetingRoom.participants.slice(1, 4))
const otherParticipantCount = computed(() =>
  Math.max(0, boardState.meetingRoom.totalParticipants - boardState.meetingRoom.participants.length)
)
const activeTab = ref('chat')
const activeContactId = ref('')
const activeContact = computed(() =>
  boardState.meetingRoom.directContacts.find(item => item.id === activeContactId.value)
)
const pinnedParticipantId = ref('')
const modal = ref('')
const draft = ref('')
const messageList = ref(null)
const showMore = ref(false)
const activeControls = reactive(new Set(['chat']))
const moreOptions = reactive({ blur: false, fullscreen: false })
const messagePlaceholder = computed(() => {
  if (activeTab.value === 'chat') return '메시지를 입력하세요...'
  if (!activeContact.value) return '대화 상대를 선택하세요...'
  return `${activeContact.value.name}에게 메시지 보내기...`
})
const helpItems = [
  { title: '마이크와 카메라', description: '하단 버튼을 눌러 마이크와 카메라를 켜거나 끌 수 있습니다.' },
  { title: '화면 고정', description: '참가자 영상에 마우스를 올린 뒤 핀 버튼을 누르면 화면을 고정할 수 있습니다.' },
  { title: '채팅과 다이렉트', description: '전체 채팅을 사용하거나 참가자를 선택해 1:1 메시지를 보낼 수 있습니다.' },
  { title: '화면 공유', description: '공유 버튼을 누르면 화면이나 특정 창을 팀원에게 공유할 수 있습니다.' }
]

function selectTab(tab) {
  activeTab.value = tab
  if (tab === 'chat') activeControls.add('chat')
  else activeControls.delete('chat')
  if (tab === 'chat') activeContactId.value = ''
}

function togglePin(participant) {
  const willPin = pinnedParticipantId.value !== participant.id
  pinnedParticipantId.value = willPin ? participant.id : ''
  notify(willPin ? `${participant.displayName} 화면을 고정했습니다.` : '화면 고정을 해제했습니다.')
}

async function toggleParticipantMic(participant) {
  try {
    await boardStore.updateParticipant(meetingId.value, participant.id, {
      muted: !participant.muted
    })
    notify(participant.muted ? '마이크를 켰습니다.' : '마이크를 음소거했습니다.')
  } catch (error) {
    notify(error?.message || '마이크 상태를 변경하지 못했습니다.')
  }
}

function handleControl(id) {
  if (id === 'people') {
    modal.value = 'participants'
    return
  }
  if (id === 'chat') {
    selectTab('chat')
    return
  }
  if (id === 'more') {
    showMore.value = !showMore.value
    if (showMore.value) activeControls.add(id)
    else activeControls.delete(id)
    return
  }
  if (activeControls.has(id)) activeControls.delete(id)
  else activeControls.add(id)
  const control = meetingControls.find(item => item.id === id)
  notify(`${control?.label || '회의'} 기능을 ${activeControls.has(id) ? '켰습니다.' : '껐습니다.'}`)
}

function toggleMoreOption(key, label) {
  moreOptions[key] = !moreOptions[key]
  notify(`${label} 기능을 ${moreOptions[key] ? '켰습니다.' : '껐습니다.'}`)
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

.meeting-more-menu > button > i img {
  display: block;
  width: 16px;
  height: 16px;
}
</style>
