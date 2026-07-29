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
          <button type="button" :disabled="resetting" @click="resetPassword">
            비밀번호를 잊으셨나요?
          </button>
        </span>
        <div class="password-box">
          <input v-model="form.password" :type="showPassword ? 'text' : 'password'" required />
          <PasswordVisibilityButton
            :visible="showPassword"
            @toggle="showPassword = !showPassword"
          />
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
import { useRoute, useRouter } from 'vue-router'
import AppLogo from '../../../shared/components/AppLogo.vue'
import PasswordVisibilityButton from '../../../shared/components/PasswordVisibilityButton.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../stores/authStore'

const route = useRoute()
const router = useRouter()
const { notify } = useToast()
const form = reactive({ email: '', password: '' })
const showPassword = ref(false)
const submitting = ref(false)
const resetting = ref(false)

async function resetPassword() {
  if (!form.email) {
    notify('비밀번호를 재설정할 이메일을 먼저 입력해 주세요.')
    return
  }
  resetting.value = true
  try {
    await authStore.resetPassword(form.email)
    notify('비밀번호 재설정 안내를 이메일로 전송했습니다.')
  } catch (error) {
    notify(error?.message || '비밀번호 재설정을 요청하지 못했습니다.')
  } finally {
    resetting.value = false
  }
}

async function submit() {
  submitting.value = true
  try {
    const result = await authStore.login(form)
    const redirect =
      typeof route.query.redirect === 'string' ? route.query.redirect : '/home'
    await router.push(result.onboarded ? redirect : '/onboarding')
  } catch (error) {
    notify(error?.message || '로그인하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>
