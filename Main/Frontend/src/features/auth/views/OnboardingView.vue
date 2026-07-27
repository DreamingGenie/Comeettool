<template>
  <main class="onboarding-page">
    <div class="auth-logo"><AppLogo /></div>
    <section class="onboarding-card">
      <div class="step-head">
        <b>STEP {{ paddedStep }}/{{ paddedTotal }}</b>
        <span>{{ currentStep.label }}</span>
      </div>
      <div class="progress"><i :style="{ width: `${progress}%` }"></i></div>
      <h1>{{ currentStep.title }}</h1>

      <div v-if="currentStep.options" class="choice-grid">
        <button
          v-for="option in currentStep.options"
          :key="option"
          type="button"
          :class="{ active: answers[step] === option }"
          @click="answers[step] = option"
        >
          {{ option }}
        </button>
      </div>
      <template v-else>
        <p class="choice-label">연령대</p>
        <div class="choice-row">
          <button
            v-for="option in currentStep.ageOptions"
            :key="option"
            type="button"
            :class="{ active: answers.age === option }"
            @click="answers.age = option"
          >
            {{ option }}
          </button>
        </div>
        <p class="choice-label">성별</p>
        <div class="choice-row">
          <button
            v-for="option in currentStep.genderOptions"
            :key="option.value"
            type="button"
            :class="{ active: answers.gender === option.value }"
            @click="answers.gender = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </template>

      <footer class="step-footer">
        <button class="later" type="button" @click="$router.push('/home')">나중에 하기</button>
        <button class="primary" type="button" :disabled="submitting" @click="next">
          {{ isLast ? '시작' : '다음' }} →
        </button>
      </footer>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppLogo from '../../../shared/components/AppLogo.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../../user/composables/useUserPage'
import { userStore } from '../../user/stores/userStore'

const router = useRouter()
const { notify } = useToast()
const { userState } = useUserPage()
const step = ref(0)
const submitting = ref(false)
const answers = reactive({})
const steps = computed(() => userState.onboarding.steps)
const currentStep = computed(() => steps.value[step.value] || { title: '', label: '', options: [] })
const isLast = computed(() => step.value >= steps.value.length - 1)
const progress = computed(() => ((step.value + 1) / Math.max(steps.value.length, 1)) * 100)
const paddedStep = computed(() => String(step.value + 1).padStart(2, '0'))
const paddedTotal = computed(() => String(steps.value.length).padStart(2, '0'))

async function next() {
  if (!isLast.value) {
    step.value += 1
    return
  }
  submitting.value = true
  try {
    await userStore.saveOnboarding({ completed: true, answers })
    await router.push('/home')
  } catch (error) {
    notify(error?.message || '온보딩 정보를 저장하지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>
