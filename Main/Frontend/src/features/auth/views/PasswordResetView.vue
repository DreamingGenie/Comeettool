<template>
  <main class="auth-page password-reset-page">
    <div class="auth-logo"><AppLogo /></div>

    <section v-if="sent" class="auth-card reset-result" aria-live="polite">
      <span class="reset-result-icon" aria-hidden="true">✓</span>
      <h1>이메일을 확인해 주세요</h1>
      <p>
        <b>{{ form.email }}</b> 주소로 비밀번호 재설정 안내를 전송했습니다.
        메일이 보이지 않으면 스팸함도 확인해 주세요.
      </p>
      <button type="button" class="primary block" @click="router.push('/login')">
        로그인으로 돌아가기
      </button>
      <button type="button" class="reset-resend" :disabled="submitting" @click="submit">
        {{ submitting ? '다시 전송 중...' : '이메일 다시 전송' }}
      </button>
    </section>

    <form v-else class="auth-card" @submit.prevent="submit">
      <small class="reset-eyebrow">PASSWORD RESET</small>
      <h1>비밀번호 재설정</h1>
      <p class="reset-description">
        가입한 이메일을 입력하면 비밀번호를 다시 설정할 수 있는 안내를 보내드릴게요.
      </p>

      <label class="field">
        이메일 주소
        <input
          v-model.trim="form.email"
          type="email"
          placeholder="example@committool.com"
          autocomplete="email"
          required
        />
      </label>

      <button class="primary block" :disabled="submitting">
        {{ submitting ? '전송 중...' : '재설정 안내 받기 →' }}
      </button>
      <button type="button" class="reset-back" @click="router.push('/login')">
        ← 로그인으로 돌아가기
      </button>
    </form>

    <div class="auth-links">이용약관　 개인정보처리방침　 고객지원</div>
  </main>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppLogo from '../../../shared/components/AppLogo.vue'
import { useToast } from '../../../shared/composables/useToast'
import { authStore } from '../stores/authStore'

const router = useRouter()
const route = useRoute()
const { notify } = useToast()
const form = reactive({
  email: typeof route.query.email === 'string' ? route.query.email : ''
})
const submitting = ref(false)
const sent = ref(false)

async function submit() {
  if (!form.email || submitting.value) return

  submitting.value = true
  try {
    await authStore.resetPassword(form.email)
    sent.value = true
    notify('비밀번호 재설정 안내를 이메일로 전송했습니다.')
  } catch (error) {
    notify(error?.message || '비밀번호 재설정을 요청하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.password-reset-page .auth-card {
  width: min(420px, calc(100vw - 32px));
}

.reset-eyebrow {
  color: #7184ed;
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.2px;
}

.reset-description,
.reset-result p {
  margin: -4px 0 22px;
  color: #727c90;
  font-size: 12px;
  line-height: 1.7;
}

.reset-back,
.reset-resend {
  display: block;
  margin: 18px auto 0;
  border: 0;
  background: transparent;
  color: #687287;
  font-size: 11px;
}

.reset-back:hover,
.reset-resend:hover {
  color: #566fea;
  text-decoration: underline;
}

.reset-result {
  text-align: center;
}

.reset-result-icon {
  display: grid;
  place-items: center;
  width: 56px;
  height: 56px;
  margin: 0 auto 18px;
  border-radius: 50%;
  background: #eaf8f1;
  color: #159264;
  font-size: 24px;
  font-weight: 800;
}

.reset-result p {
  margin-top: 0;
}

.reset-result p b {
  color: #29344a;
}
</style>
