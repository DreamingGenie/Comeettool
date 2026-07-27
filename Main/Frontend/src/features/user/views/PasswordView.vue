<template>
  <AppShell settings :user="userState.profile || {}">
    <template #navigation>
      <SettingsSidebar active="password" />
    </template>
    <AsyncState
      v-if="userState.loading || userState.error"
      :loading="userState.loading"
      :error="userState.error"
      :retry="reloadUser"
    />
    <div v-else class="password-layout password-settings">
      <form class="password-card" @submit.prevent="save">
        <h1>비밀번호 변경</h1>
        <label class="field">
          현재 비밀번호
          <div class="password-box">
            <input v-model="form.currentPassword" :type="visible.current ? 'text' : 'password'" required />
            <button type="button" @click="visible.current = !visible.current">◉</button>
          </div>
        </label>
        <hr />
        <label class="field">
          새로운 비밀번호
          <div class="password-box">
            <input v-model="form.newPassword" :type="visible.next ? 'text' : 'password'" required />
            <button type="button" @click="visible.next = !visible.next">◉</button>
          </div>
        </label>
        <div class="strength-label">
          <b>비밀번호 강도</b><span>{{ strength.label }}</span>
        </div>
        <div class="strength-bar">
          <i :style="{ width: `${strength.score}%`, background: strength.color }"></i>
        </div>
        <label class="field">
          새로운 비밀번호 확인
          <div class="password-box">
            <input v-model="form.newPasswordConfirm" :type="visible.confirm ? 'text' : 'password'" required />
            <button type="button" @click="visible.confirm = !visible.confirm">◉</button>
          </div>
        </label>
        <div class="password-actions">
          <button class="primary" :disabled="saving">비밀번호 변경 →</button>
          <button type="button" @click="$router.push('/profile')">취소</button>
        </div>
      </form>
      <aside>
        <section class="requirements">
          <h2>보안 요구사항</h2>
          <article v-for="item in requirements" :key="item.title">
            <i>✓</i>
            <div><b>{{ item.title }}</b><p>{{ item.description }}</p></div>
          </article>
        </section>
      </aside>
    </div>
  </AppShell>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import AppShell from '../../../shared/components/AppShell.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import SettingsSidebar from '../../../shared/components/SettingsSidebar.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../composables/useUserPage'
import { userStore } from '../stores/userStore'

const { userState, reloadUser } = useUserPage()
const { notify } = useToast()
const saving = ref(false)
const form = reactive({ currentPassword: '', newPassword: '', newPasswordConfirm: '' })
const visible = reactive({ current: false, next: false, confirm: false })
const requirements = [
  { title: '최소 12자 이상', description: '긴 비밀번호일수록 무차별 대입 공격에 더 안전합니다.' },
  { title: '복잡성', description: '문자(A-z), 숫자(0-9), 특수문자(!@#)를 조합하세요.' },
  { title: '반복 피하기', description: '이메일 주소 일부나 단순한 연속 문자를 사용하지 마세요.' }
]
const strength = computed(() => {
  const password = form.newPassword
  let points = 0
  if (password.length >= 12) points += 40
  if (/[a-z]/i.test(password) && /\d/.test(password)) points += 30
  if (/[^a-z0-9]/i.test(password)) points += 30
  if (points >= 100) return { score: 100, label: '강함', color: '#1fbd78' }
  if (points >= 60) return { score: 65, label: '보통', color: '#ffb820' }
  if (password) return { score: 25, label: '약함', color: '#c21c24' }
  return { score: 0, label: '-', color: '#c21c24' }
})

async function save() {
  if (form.newPassword !== form.newPasswordConfirm) {
    notify('새 비밀번호가 일치하지 않습니다.')
    return
  }
  if (strength.value.score < 60) {
    notify('보안 요구사항에 맞는 비밀번호를 입력해 주세요.')
    return
  }
  saving.value = true
  try {
    await userStore.changePassword({ ...form })
    Object.assign(form, { currentPassword: '', newPassword: '', newPasswordConfirm: '' })
    notify('비밀번호를 변경했습니다.')
  } catch (error) {
    notify(error?.message || '비밀번호를 변경하지 못했습니다.')
  } finally {
    saving.value = false
  }
}
</script>
