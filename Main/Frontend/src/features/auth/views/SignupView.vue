<template>
  <main class="auth-page">
    <div class="auth-logo"><AppLogo /></div>
    <form class="auth-card" @submit.prevent="submit">
      <h1>회원가입</h1>
      <label class="field">
        이메일 주소
        <input v-model.trim="form.email" type="email" placeholder="example@committool.com" required />
      </label>
      <label class="field">
        비밀번호
        <div class="password-box">
          <input
            v-model="form.password"
            :type="showPassword ? 'text' : 'password'"
            minlength="8"
            maxlength="20"
            autocomplete="new-password"
            required
          />
          <PasswordVisibilityButton
            :visible="showPassword"
            @toggle="showPassword = !showPassword"
          />
        </div>
      </label>
      <label class="field">
        비밀번호 확인
        <div class="password-box">
          <input
            v-model="form.passwordConfirm"
            :type="showConfirm ? 'text' : 'password'"
            minlength="8"
            maxlength="20"
            autocomplete="new-password"
            required
          />
          <PasswordVisibilityButton
            label="비밀번호 확인"
            :visible="showConfirm"
            @toggle="showConfirm = !showConfirm"
          />
        </div>
      </label>
      <button class="primary block" :disabled="submitting">회원가입 →</button>
      <div class="auth-divider"></div>
      <p class="switch">
        이미 계정이 있으신가요?
        <button type="button" @click="$router.push('/login')">로그인</button>
      </p>
    </form>
    <div class="auth-links">이용약관　 개인정보처리방침　 고객지원</div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLogo from '../../../shared/components/AppLogo.vue'
import PasswordVisibilityButton from '../../../shared/components/PasswordVisibilityButton.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../stores/authStore'

const router = useRouter()
const { notify } = useToast()
const form = reactive({ email: '', password: '', passwordConfirm: '' })
const showPassword = ref(false)
const showConfirm = ref(false)
const submitting = ref(false)

async function submit() {
  const passwordPattern =
    /^(?=.*[A-Za-z])(?=.*\d)(?=.*[!@#$%^&*()_+=-])[A-Za-z\d!@#$%^&*()_+=-]{8,20}$/
  if (!passwordPattern.test(form.password)) {
    notify('비밀번호는 8~20자의 영문, 숫자, 특수문자를 포함해야 합니다.')
    return
  }
  if (form.password !== form.passwordConfirm) {
    notify('비밀번호가 일치하지 않습니다.')
    return
  }
  submitting.value = true
  try {
    await authStore.signup(form)
    await router.push('/login')
  } catch (error) {
    notify(error?.message || '회원가입하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>
