<template>
  <main class="auth-page">
    <div class="auth-logo"><AppLogo /></div>
    <form class="auth-card" @submit.prevent="submit">
      <h1>로그인</h1>
      <label class="field">
        이메일 주소
        <input v-model.trim="form.email" type="email" placeholder="example@committool.com" required />
      </label>
      <label class="field">
        <span class="field-line">
          비밀번호
          <button type="button">비밀번호를 잊으셨나요?</button>
        </span>
        <div class="password-box">
          <input v-model="form.password" :type="showPassword ? 'text' : 'password'" required />
          <button type="button" aria-label="비밀번호 표시 전환" @click="showPassword = !showPassword">◉</button>
        </div>
      </label>
      <button class="primary block" :disabled="submitting">로그인 →</button>
      <div class="auth-divider"></div>
      <p class="switch">
        아직 계정이 없으신가요?
        <button type="button" @click="$router.push('/signup')">회원가입</button>
      </p>
    </form>
    <div class="auth-links">이용약관　 개인정보처리방침　 고객지원</div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLogo from '../../../shared/components/AppLogo.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../stores/authStore'
import { userStore } from '../../user/stores/userStore'

const router = useRouter()
const { notify } = useToast()
const form = reactive({ email: '', password: '' })
const showPassword = ref(false)
const submitting = ref(false)

async function submit() {
  submitting.value = true
  try {
    const result = await authStore.login(form)
    userStore.setProfile(result.user)
    await router.push('/onboarding')
  } catch (error) {
    notify(error?.message || '로그인하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>
