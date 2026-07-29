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
        <div>
          <span>ACCOUNT SETTINGS</span>
          <h1>프로필 설정</h1>
          <p>내 정보를 최신 상태로 유지하고 팀원들에게 나를 소개해 보세요.</p>
        </div>
        <div class="profile-color-preview" aria-label="사용자 색상 미리보기">
          <i :style="{ backgroundColor: selectedColor }">{{ avatarPreview }}</i>
          <span>
            <small>프로필 미리보기</small>
            <b>{{ form.nickname || '사용자' }}</b>
          </span>
        </div>
      </header>
      <div class="profile-settings-body">
        <aside class="photo-card">
          <div class="photo-circle">
            <img class="side-icon" src="/assets/icons/camera.svg" alt="" />
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
              <input v-model.trim="form.nickname" :maxlength="nicknameMaxLength" />
              <small>{{ form.nickname.length }}/{{ nicknameMaxLength }}</small>
            </div>
          </label>
          <label class="field">
            전화번호
            <input
              :value="form.phone"
              type="tel"
              inputmode="numeric"
              maxlength="13"
              placeholder="010-1234-5678"
              @input="onPhoneInput"
            />
          </label>
          <label class="field profile-bio">
            <span class="field-label-with-meta">
              자기소개
              <small>{{ form.userDescription.length }}/{{ bioMaxLength }}</small>
            </span>
            <textarea
              v-model.trim="form.userDescription"
              :maxlength="bioMaxLength"
              placeholder="팀원들에게 나를 소개해 보세요."
            ></textarea>
          </label>
          <fieldset class="profile-color-field">
            <legend>사용자 색상</legend>
            <small>프로필과 팀원 목록에서 표시할 색상을 선택해 주세요.</small>
            <AppColorPicker v-model="form.userColor" :options="colorOptions" />
          </fieldset>
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
                :class="{ active: form.sex === gender.value }"
              >
                <input v-model="form.sex" type="radio" :value="gender.value" />
                {{ gender.label }}
              </label>
            </fieldset>
            <label class="field">
              연령대
              <select v-model.number="form.age">
                <option
                  v-for="option in userState.profileOptions.ageGroups"
                  :key="option.value"
                  :value="option.value"
                >
                  {{ option.label }}
                </option>
              </select>
            </label>
            <label class="field">
              직군
              <select v-model="form.jobFamily" @change="onJobFamilyChange">
                <option v-for="option in userState.profileOptions.jobFamilies" :key="option">
                  {{ option }}
                </option>
              </select>
            </label>
            <label class="field">
              세부 직무
              <select v-model="form.jobRole">
                <option v-for="option in availableJobRoles" :key="option">
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
import { computed, reactive, ref, watch } from 'vue'
import AppShell from '../../../shared/components/AppShell.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import AppColorPicker from '../../../shared/components/AppColorPicker.vue'
import SettingsSidebar from '../components/SettingsSidebar.vue'
import { useToast } from '../../../shared/composables/useToast'
import { useUserPage } from '../composables/useUserPage'
import { userStore } from '../stores/userStore'

const { userState, reloadUser } = useUserPage()
const { notify } = useToast()
const nicknameMaxLength = 20
const bioMaxLength = 255
const saving = ref(false)
const form = reactive({
  nickname: '',
  phone: '',
  userDescription: '',
  userColor: '',
  sex: '',
  age: '',
  jobFamily: '',
  jobRole: ''
})

const colorOptions = computed(() => userState.profileOptions.colors || [])
const availableJobRoles = computed(
  () =>
    userState.profileOptions.jobRolesByFamily?.[form.jobFamily] ||
    userState.profileOptions.jobRoles ||
    []
)
const selectedColor = computed(
  () => form.userColor || colorOptions.value[0]?.value || '#496FBD'
)
const avatarPreview = computed(
  () =>
    form.nickname.trim().slice(0, 2) ||
    userState.profile?.avatarText ||
    '나'
)

watch(
  [() => userState.profile, colorOptions],
  ([profile, colors]) => {
    if (!profile) return
    Object.assign(form, profile)
    form.userDescription = profile.userDescription || ''
    if (!form.userColor && colors.length) form.userColor = colors[0].value
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

function onPhoneInput(event) {
  const digits = event.target.value.replace(/\D/g, '').slice(0, 11)
  let formatted = digits

  if (digits.length > 7) {
    formatted = `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7)}`
  } else if (digits.length > 3) {
    formatted = `${digits.slice(0, 3)}-${digits.slice(3)}`
  }

  form.phone = formatted
  event.target.value = formatted
}

function onJobFamilyChange() {
  if (!availableJobRoles.value.includes(form.jobRole)) {
    form.jobRole = ''
  }
}
</script>
