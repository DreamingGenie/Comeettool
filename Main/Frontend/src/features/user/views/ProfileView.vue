<template>
  <AppShell settings :user="userState.profile || {}">
    <template #navigation>
      <SettingsSidebar active="profile" />
    </template>
    <AsyncState
      v-if="userState.loading || userState.error"
      :loading="userState.loading"
      :error="userState.error"
      :retry="reloadUser"
    />
    <form v-else class="profile-settings" @submit.prevent="save">
      <header class="account-heading">
        <span>ACCOUNT SETTINGS</span>
        <h1>프로필 설정</h1>
        <p>내 정보를 최신 상태로 유지하고 팀원들에게 나를 소개해 보세요.</p>
      </header>
      <div class="profile-settings-body">
        <aside class="photo-card">
          <div class="photo-circle">
            <img class="side-icon" src="/assets/icons/camera.svg" alt="" />
            <button type="button" aria-label="프로필 사진 추가">＋</button>
          </div>
          <b>프로필 사진</b>
          <small>권장 크기 400 × 400px<br />JPG, PNG · 최대 5MB</small>
          <button type="button" class="upload-photo" @click="notify('사진 업로드 기능은 API 연결 후 제공됩니다.')">
            사진 업로드
          </button>
          <p>✓ 얼굴이 잘 보이는 사진을 권장해요.</p>
        </aside>
        <section class="profile-fields">
          <div class="form-section-title">
            <b>기본 정보</b>
            <small>서비스에서 표시되는 정보를 입력해 주세요.</small>
          </div>
          <label class="field">
            닉네임
            <div class="input-with-meta">
              <span>♙</span>
              <input v-model.trim="form.nickname" maxlength="10" />
              <small>{{ form.nickname.length }}/10</small>
            </div>
          </label>
          <label class="field">
            전화번호
            <input v-model.trim="form.phone" />
          </label>
          <div class="form-section-title role-title">
            <b>직무 및 프로필</b>
            <small>맞춤형 팀 경험을 위해 직무 정보를 선택해 주세요.</small>
          </div>
          <div class="profile-grid">
            <fieldset>
              <legend>성별</legend>
              <label
                v-for="gender in userState.profileOptions.genders"
                :key="gender.value"
                class="radio-card"
                :class="{ active: form.gender === gender.value }"
              >
                <input v-model="form.gender" type="radio" :value="gender.value" />
                {{ gender.label }}
              </label>
            </fieldset>
            <label class="field">
              연령대
              <select v-model="form.ageGroup">
                <option v-for="option in userState.profileOptions.ageGroups" :key="option">
                  {{ option }}
                </option>
              </select>
            </label>
            <label class="field">
              직군
              <select v-model="form.jobGroup">
                <option v-for="option in userState.profileOptions.jobGroups" :key="option">
                  {{ option }}
                </option>
              </select>
            </label>
            <label class="field">
              세부 직무
              <select v-model="form.job">
                <option v-for="option in userState.profileOptions.jobs" :key="option">
                  {{ option }}
                </option>
              </select>
            </label>
          </div>
          <button class="primary profile-submit" :disabled="saving">저장</button>
        </section>
      </div>
    </form>
  </AppShell>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import AppShell from '../../../shared/components/AppShell.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import SettingsSidebar from '../../../shared/components/SettingsSidebar.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../composables/useUserPage'
import { userStore } from '../stores/userStore'

const { userState, reloadUser } = useUserPage()
const { notify } = useToast()
const saving = ref(false)
const form = reactive({
  nickname: '',
  phone: '',
  gender: '',
  ageGroup: '',
  jobGroup: '',
  job: ''
})

watch(
  () => userState.profile,
  profile => {
    if (profile) Object.assign(form, profile)
  },
  { immediate: true }
)

async function save() {
  saving.value = true
  try {
    await userStore.updateProfile({ ...form })
    notify('프로필을 저장했습니다.')
  } catch (error) {
    notify(error?.message || '프로필을 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}
</script>
