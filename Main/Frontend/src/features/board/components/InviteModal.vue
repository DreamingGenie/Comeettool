<template>
  <BaseModal modal-class="invite-modal" @close="$emit('close')">
    <header class="invite-header">
      <span class="invite-header__icon" aria-hidden="true">
        <i></i><i></i>
      </span>
      <div>
        <small>TEAM INVITATION</small>
        <h2>멤버 초대 및 공유</h2>
        <p>이메일 또는 초대 링크로 팀원을 초대해 보세요.</p>
      </div>
    </header>

    <section class="invite-section" aria-labelledby="email-invite-title">
      <div class="invite-section__title">
        <div>
          <h3 id="email-invite-title">이메일로 초대</h3>
          <p>초대할 멤버의 이메일과 권한을 선택해 주세요.</p>
        </div>
      </div>

      <form class="invite-form" @submit.prevent="invite">
        <div class="invite-email">
          <label for="invite-email-input">이름 또는 이메일</label>
          <input
            id="invite-email-input"
            v-model.trim="email"
            type="email"
            placeholder="email@example.com"
            autocomplete="email"
            required
            @focus="scheduleSearch"
            @input="scheduleSearch"
          />
          <div
            v-if="showSearchResults"
            class="invite-search-results"
            aria-live="polite"
          >
            <p v-if="userSearch.loading">사용자를 검색하고 있습니다.</p>
            <p v-else-if="userSearch.error" class="error">{{ userSearch.error }}</p>
            <template v-else-if="visibleSearchResults.length">
              <button
                v-for="user in visibleSearchResults"
                :key="user.userId || user.email"
                type="button"
                @click="selectUser(user)"
              >
                <i :style="{ backgroundColor: user.userColor }">
                  {{ user.avatarText }}
                </i>
                <span>
                  <b>{{ user.nickname || '이름 없음' }}</b>
                  <small>{{ user.email }}</small>
                </span>
              </button>
            </template>
            <p v-else>일치하는 사용자가 없습니다.</p>
          </div>
        </div>
        <label class="invite-permission">
          <span>권한</span>
          <select v-model="permission">
            <option value="MEMBER">편집 가능</option>
            <option value="GUEST">보기 전용</option>
          </select>
        </label>
        <button class="primary invite-submit" type="submit" :disabled="submitting">
          <span>{{ submitting ? '전송 중' : '초대 발송' }}</span>
          <b aria-hidden="true">→</b>
        </button>
      </form>
    </section>

    <section class="invite-section member-section" aria-labelledby="member-list-title">
      <div class="invite-section__title member-heading">
        <div>
          <h3 id="member-list-title">현재 멤버</h3>
          <p>이 팀 스페이스에 참여 중인 멤버입니다.</p>
        </div>
        <strong>{{ members.length }}명</strong>
      </div>

      <div class="invite-list">
        <article v-for="(member, index) in members" :key="member.id">
          <i class="invite-avatar" :style="{ '--avatar-hue': `${(index * 43 + 222) % 360}` }">
            {{ member.avatarText }}
          </i>
          <span class="invite-member">
            <b>{{ member.name }}</b>
            <small>{{ member.email }}</small>
          </span>
          <em :class="{ owner: member.role === 'OWNER' }">{{ roleLabel(member.role) }}</em>
        </article>
      </div>
    </section>

    <section class="invite-link-section" aria-labelledby="invite-link-title">
      <span class="link-icon" aria-hidden="true">
        <img src="/assets/icons/linkcopy.svg" alt="" />
      </span>
      <div>
        <h3 id="invite-link-title">초대 링크 공유</h3>
        <p>링크를 받은 사용자는 설정된 기본 권한으로 참여할 수 있습니다.</p>
      </div>
      <button class="copy-link-button" type="button" @click="copyLink">
        <img src="/assets/icons/linkcopy.svg" alt="" aria-hidden="true" />
        {{ copied ? '복사 완료' : '링크 복사' }}
      </button>
    </section>
  </BaseModal>
</template>

<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { userStore } from '../../user/stores/userStore'
import { boardStore } from '../stores/boardStore'

const props = defineProps({
  teamId: { type: String, required: true },
  members: { type: Array, default: () => [] }
})

defineEmits(['close'])

const { notify } = useToast()
const email = ref('')
const permission = ref('MEMBER')
const submitting = ref(false)
const copied = ref(false)
const searchOpen = ref(false)
const userSearch = userStore.state.search
const visibleSearchResults = computed(() => {
  const existingUsers = new Set(
    props.members.flatMap(member => [
      member.userId,
      member.id,
      member.email?.toLowerCase()
    ])
  )
  return userSearch.results.filter(
    user =>
      !existingUsers.has(user.userId) &&
      !existingUsers.has(user.email?.toLowerCase())
  )
})
const showSearchResults = computed(
  () =>
    searchOpen.value &&
    email.value.length >= 2 &&
    (userSearch.loading ||
      Boolean(userSearch.error) ||
      userSearch.query === email.value)
)
let copiedTimer
let searchTimer

const roleLabel = role => (role === 'OWNER' ? 'OWNER' : role === 'GUEST' ? 'GUEST' : 'MEMBER')

async function invite() {
  if (!email.value || submitting.value) return

  submitting.value = true
  try {
    await boardStore.inviteMember(props.teamId, {
      email: email.value,
      permission: permission.value
    })
    notify(`${email.value} 주소로 초대를 보냈습니다.`)
    email.value = ''
    searchOpen.value = false
    userStore.clearSearch()
  } catch (error) {
    notify(error?.message || '초대를 보내지 못했습니다.')
  } finally {
    submitting.value = false
  }
}

function scheduleSearch() {
  window.clearTimeout(searchTimer)
  searchOpen.value = true

  if (email.value.length < 2) {
    userStore.clearSearch()
    return
  }

  searchTimer = window.setTimeout(() => {
    userStore.searchUsers(email.value).catch(() => undefined)
  }, 300)
}

function selectUser(user) {
  email.value = user.email
  searchOpen.value = false
  userStore.clearSearch()
}

async function copyLink() {
  const inviteUrl = `${window.location.origin}/teams/${props.teamId}/join`

  try {
    await navigator.clipboard.writeText(inviteUrl)
    copied.value = true
    window.clearTimeout(copiedTimer)
    copiedTimer = window.setTimeout(() => {
      copied.value = false
    }, 1800)
    notify('초대 링크를 복사했습니다.')
  } catch {
    notify('초대 링크를 복사하지 못했습니다.')
  }
}

onBeforeUnmount(() => {
  window.clearTimeout(copiedTimer)
  window.clearTimeout(searchTimer)
  userStore.clearSearch()
})
</script>

<style scoped>
:deep(.invite-modal) {
  width: min(640px, calc(100vw - 32px));
  max-height: calc(100vh - 40px);
  padding: 30px;
  overflow-y: auto;
  border: 1px solid #d9deea;
  border-radius: 24px;
  box-shadow: 0 28px 80px #23335533;
}

:deep(.invite-modal .close) {
  top: 22px;
  right: 24px;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #596276;
  font-size: 22px;
}

:deep(.invite-modal .close:hover) {
  background: #f0f2f7;
  color: #182238;
  transform: rotate(90deg);
}

.invite-header {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 48px 22px 0;
  border-bottom: 1px solid #e6e9f0;
}

.invite-header__icon {
  position: relative;
  flex: 0 0 48px;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #eef1ff;
}

.invite-header__icon i {
  position: absolute;
  top: 13px;
  width: 12px;
  height: 12px;
  border: 2px solid #6075e8;
  border-radius: 50%;
}

.invite-header__icon i:first-child {
  left: 11px;
}

.invite-header__icon i:last-child {
  right: 9px;
  opacity: 0.7;
}

.invite-header__icon::before,
.invite-header__icon::after {
  position: absolute;
  bottom: 9px;
  width: 18px;
  height: 9px;
  border: 2px solid #6075e8;
  border-bottom: 0;
  border-radius: 12px 12px 0 0;
  content: '';
}

.invite-header__icon::before {
  left: 6px;
}

.invite-header__icon::after {
  right: 5px;
  opacity: 0.7;
}

.invite-header small {
  color: #566fea;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.invite-header h2 {
  margin: 4px 0;
  color: #182238;
  font-size: 22px;
}

.invite-header p,
.invite-section__title p,
.invite-link-section p {
  margin: 0;
  color: #7a8395;
  font-size: 11px;
  line-height: 1.55;
}

.invite-section {
  padding: 22px 0;
  border-bottom: 1px solid #e6e9f0;
}

.invite-section__title {
  margin-bottom: 14px;
}

.invite-section__title h3,
.invite-link-section h3 {
  margin: 0 0 4px;
  font-size: 13px;
}

.invite-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px 104px;
  gap: 10px;
  align-items: end;
}

.invite-form label {
  display: grid;
  gap: 6px;
  color: #4f586a;
  font-size: 10px;
  font-weight: 700;
}

.invite-email {
  position: relative;
  display: grid;
  gap: 6px;
}

.invite-email > label {
  color: #4f586a;
  font-size: 10px;
  font-weight: 700;
}

.invite-form input,
.invite-form select {
  width: 100%;
  height: 44px;
  border: 1px solid #d5dae5;
  border-radius: 9px;
  outline: none;
  background: #fff;
  padding: 0 12px;
  font-size: 12px;
}

.invite-form input:focus,
.invite-form select:focus {
  border-color: #7184ed;
  box-shadow: 0 0 0 3px #6175e61f;
}

.invite-search-results {
  position: absolute;
  top: calc(100% + 5px);
  right: 0;
  left: 0;
  z-index: 8;
  max-height: 210px;
  overflow-y: auto;
  padding: 6px;
  border: 1px solid #d9deea;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 14px 34px #1d2c4d29;
}

.invite-search-results > p {
  margin: 0;
  padding: 12px 10px;
  color: #778095;
  font-size: 10px;
  text-align: center;
}

.invite-search-results > p.error {
  color: #b42332;
}

.invite-search-results > button {
  display: grid;
  grid-template-columns: 32px minmax(0, 1fr);
  align-items: center;
  gap: 9px;
  width: 100%;
  min-height: 46px;
  padding: 6px 8px;
  border: 0;
  border-radius: 8px;
  background: #fff;
  text-align: left;
}

.invite-search-results > button:hover {
  background: #f3f5fc;
}

.invite-search-results > button > i {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 10px;
  color: #fff;
  font-size: 10px;
  font-style: normal;
  font-weight: 800;
}

.invite-search-results > button > span {
  display: grid;
  min-width: 0;
  gap: 2px;
}

.invite-search-results b {
  font-size: 10px;
}

.invite-search-results small {
  overflow: hidden;
  color: #788194;
  font-size: 9px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invite-submit {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 44px;
  padding: 0 14px;
  border-radius: 9px;
  font-size: 11px;
}

.invite-submit:disabled {
  cursor: wait;
  opacity: 0.65;
}

.invite-submit b {
  font-size: 16px;
  line-height: 1;
}

.member-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.member-heading strong {
  padding: 5px 9px;
  border-radius: 999px;
  background: #eef1ff;
  color: #5269dc;
  font-size: 10px;
}

.invite-list {
  display: grid;
  max-height: 224px;
  overflow-y: auto;
}

.invite-list article {
  display: grid;
  grid-template-columns: 38px minmax(0, 1fr) auto;
  align-items: center;
  gap: 11px;
  min-height: 54px;
  margin: 0;
  padding: 7px 8px;
  border-radius: 10px;
  transition: background-color 0.18s ease, transform 0.18s ease;
}

.invite-list article:hover {
  background: #f7f8fc;
  transform: translateX(2px);
}

.invite-avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 11px;
  background: hsl(var(--avatar-hue) 72% 92%);
  color: hsl(var(--avatar-hue) 48% 36%);
  font-size: 12px;
  font-style: normal;
  font-weight: 800;
}

.invite-member {
  display: grid;
  min-width: 0;
  gap: 3px;
}

.invite-member b {
  font-size: 12px;
}

.invite-member small {
  overflow: hidden;
  color: #7b8495;
  font-size: 10px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.invite-list em {
  margin-left: 0;
  padding: 4px 8px;
  border-radius: 999px;
  background: #f1f3f7;
  color: #687184;
  font-size: 8px;
  font-style: normal;
  font-weight: 800;
  letter-spacing: 0.4px;
}

.invite-list em.owner {
  background: #eaf0ff;
  color: #4964d9;
}

.invite-link-section {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  margin-top: 20px;
  padding: 14px;
  border: 1px solid #dce2f2;
  border-radius: 12px;
  background: #f8f9fd;
}

.link-icon {
  display: grid;
  place-items: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: #e9edff;
}

.link-icon img {
  display: block;
  width: 18px;
  height: 18px;
  object-fit: contain;
  filter: brightness(0) saturate(100%) invert(42%) sepia(79%) saturate(1747%)
    hue-rotate(211deg) brightness(98%) contrast(88%);
}

.copy-link-button {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 90px;
  height: 36px;
  justify-content: center;
  border: 1px solid #cfd6e8;
  border-radius: 8px;
  background: #fff;
  color: #344052;
  font-size: 10px;
  font-weight: 700;
}

.copy-link-button:hover {
  border-color: #7184ed;
  background: #eef1ff;
  color: #435bd8;
  transform: translateY(-1px);
}

.copy-link-button img {
  display: block;
  width: 14px;
  height: 14px;
  object-fit: contain;
  opacity: 0.72;
  transition: filter 0.18s ease, opacity 0.18s ease;
}

.copy-link-button:hover img {
  opacity: 1;
  filter: brightness(0) saturate(100%) invert(42%) sepia(79%) saturate(1747%)
    hue-rotate(211deg) brightness(98%) contrast(88%);
}

@media (max-width: 620px) {
  :deep(.invite-modal) {
    padding: 24px 18px;
  }

  .invite-form {
    grid-template-columns: 1fr;
  }

  .invite-link-section {
    grid-template-columns: 36px 1fr;
  }

  .copy-link-button {
    grid-column: 1 / -1;
    width: 100%;
  }
}
</style>
