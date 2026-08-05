<template>
  <AppShell settings :user="userState.profile || {}">
    <template #navigation>
      <SettingsSidebar active="account" />
    </template>

    <AsyncState
      v-if="userState.loading || userState.error"
      :loading="userState.loading"
      :error="userState.error"
      :retry="reloadUser"
    />

    <section v-else class="account-management">
      <header class="account-heading">
        <div>
          <span>ACCOUNT SETTINGS</span>
          <h1>계정 관리</h1>
          <p>계정과 서비스 이용 정보를 안전하게 관리하세요.</p>
        </div>
      </header>

      <article class="account-summary">
        <i :style="{ backgroundColor: userState.profile?.userColor || '#5f6fe5' }">
          {{ userState.profile?.avatarText || '나' }}
        </i>
        <div>
          <small>현재 로그인한 계정</small>
          <b>{{ userState.profile?.nickname || '사용자' }}</b>
          <span>{{ userState.profile?.email }}</span>
        </div>
      </article>

      <article class="account-danger-zone">
        <div>
          <small>DANGER ZONE</small>
          <h2>회원 탈퇴</h2>
          <p>
            탈퇴하면 현재 계정으로 서비스에 다시 접근할 수 없습니다.
            팀과 회의에서 생성한 데이터의 처리 방식은 서비스 정책에 따라 달라질 수 있습니다.
          </p>
        </div>
        <button type="button" class="withdraw-open" @click="openConfirm">
          회원 탈퇴
        </button>
      </article>
    </section>

    <BaseModal
      v-if="confirming"
      modal-class="account-withdraw-modal"
      @close="closeConfirm"
    >
      <div class="withdraw-modal-content">
        <span class="withdraw-warning" aria-hidden="true">!</span>
        <small>DANGER ZONE</small>
        <h2>정말 탈퇴하시겠어요?</h2>
        <p>
          이 작업은 되돌릴 수 없습니다. 계속하려면 아래 입력란에
          <b>{{ confirmationPhrase }}</b>를 입력해 주세요.
        </p>

        <label>
          확인 문구
          <input
            v-model.trim="confirmation"
            :placeholder="confirmationPhrase"
            autocomplete="off"
            @keyup.enter="withdraw"
          />
        </label>

        <div class="withdraw-actions">
          <button type="button" @click="closeConfirm">취소</button>
          <button
            type="button"
            class="withdraw-confirm"
            :disabled="!canWithdraw || withdrawing"
            @click="withdraw"
          >
            {{ withdrawing ? '탈퇴 처리 중...' : '계정 영구 탈퇴' }}
          </button>
        </div>
      </div>
    </BaseModal>
  </AppShell>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppShell from '../../../shared/components/AppShell.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../../auth/stores/authStore'
import { boardStore } from '../../board/stores/boardStore'
import SettingsSidebar from '../components/SettingsSidebar.vue'
import { useUserPage } from '../composables/useUserPage'
import { userStore } from '../stores/userStore'

const router = useRouter()
const { notify } = useToast()
const { userState, reloadUser } = useUserPage()
const confirmationPhrase = '회원 탈퇴'
const confirmation = ref('')
const confirming = ref(false)
const withdrawing = ref(false)
const canWithdraw = computed(
  () => confirmation.value === confirmationPhrase
)

function openConfirm() {
  confirmation.value = ''
  confirming.value = true
}

function closeConfirm() {
  if (withdrawing.value) return
  confirmation.value = ''
  confirming.value = false
}

async function withdraw() {
  if (!canWithdraw.value || withdrawing.value) return

  withdrawing.value = true
  try {
    await userStore.withdraw()
    boardStore.reset()
    authStore.clearSession()
    confirming.value = false
    notify('회원 탈퇴가 완료되었습니다.')
    await router.replace({ name: 'intro' })
  } catch (error) {
    notify(error?.message || '회원 탈퇴를 완료하지 못했습니다.')
  } finally {
    withdrawing.value = false
  }
}
</script>

<style scoped>
.account-management {
  display: grid;
  align-content: start;
  gap: 24px;
  height: 100%;
}

.account-summary {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border: 1px solid #dfe3eb;
  border-radius: 14px;
  background: #fff;
}

.account-summary > i {
  display: grid;
  flex: 0 0 auto;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  color: #fff;
  font-style: normal;
  font-weight: 800;
}

.account-summary > div {
  display: grid;
  gap: 3px;
}

.account-summary small,
.account-summary span {
  color: #7c8597;
  font-size: 11px;
}

.account-danger-zone {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  padding: 28px;
  border: 1px solid #efc4c8;
  border-radius: 16px;
  background: #fffafa;
}

.account-danger-zone small,
.withdraw-modal-content > small {
  color: #c63843;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1px;
}

.account-danger-zone h2 {
  margin: 7px 0;
  font-size: 18px;
}

.account-danger-zone p {
  max-width: 640px;
  margin: 0;
  color: #70798a;
  font-size: 11px;
  line-height: 1.7;
}

.withdraw-open,
.withdraw-confirm {
  flex: 0 0 auto;
  min-width: 104px;
  height: 40px;
  border: 1px solid #ce3e49;
  border-radius: 8px;
  background: #fff;
  color: #bd2f3b;
  font-weight: 700;
}

.withdraw-open:hover {
  background: #c63843;
  color: #fff;
}

:deep(.account-withdraw-modal) {
  width: min(460px, calc(100vw - 32px));
  padding: 32px;
  border-radius: 20px;
}

.withdraw-modal-content {
  display: grid;
  justify-items: start;
}

.withdraw-warning {
  display: grid;
  place-items: center;
  width: 44px;
  height: 44px;
  margin-bottom: 18px;
  border-radius: 50%;
  background: #fff0f1;
  color: #c63843;
  font-size: 22px;
  font-weight: 800;
}

.withdraw-modal-content h2 {
  margin: 6px 0 10px;
}

.withdraw-modal-content p {
  margin: 0 0 22px;
  color: #697287;
  font-size: 12px;
  line-height: 1.7;
}

.withdraw-modal-content label {
  display: grid;
  width: 100%;
  gap: 7px;
  color: #39445a;
  font-size: 11px;
  font-weight: 700;
}

.withdraw-modal-content input {
  width: 100%;
  height: 42px;
  padding: 0 12px;
  border: 1px solid #d5dae5;
  border-radius: 8px;
  outline: 0;
}

.withdraw-modal-content input:focus {
  border-color: #c63843;
  box-shadow: 0 0 0 3px #c6384318;
}

.withdraw-actions {
  display: flex;
  justify-content: flex-end;
  gap: 9px;
  width: 100%;
  margin-top: 24px;
}

.withdraw-actions button {
  height: 40px;
  padding: 0 16px;
  border: 1px solid #d5dae5;
  border-radius: 8px;
  background: #fff;
}

.withdraw-actions .withdraw-confirm {
  border-color: #c63843;
  background: #c63843;
  color: #fff;
}

.withdraw-confirm:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

@media (max-width: 640px) {
  .account-danger-zone {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
