<template>
  <BaseModal @close="$emit('close')">
    <form @submit.prevent="submit">
      <small style="color:#4285ef">NEW TEAM SPACE</small>
      <h2>새 팀 스페이스</h2>
      <TeamProfileImagePicker
        :model-value="imagePreviewUrl"
        :fallback="form.name || 'TM'"
        :color="form.color"
        label="팀 프로필 이미지"
        @file-change="selectProfileImage"
        @error="notify"
      />
      <label class="field">
        팀 이름
        <input v-model.trim="form.name" required />
      </label>
      <label class="field">
        설명
        <textarea v-model.trim="form.description"></textarea>
      </label>
      <fieldset class="team-color-field">
        <legend>
          <b>팀 색상</b><br />
          <small>팀 아이콘과 포인트 색상에 사용됩니다.</small>
        </legend>
        <AppColorPicker
          v-model="form.color"
          :options="teamCreateColors"
          subject="팀"
        />
      </fieldset>
      <button class="primary block" :disabled="submitting">팀 스페이스 만들기</button>
    </form>
  </BaseModal>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref } from 'vue'
import AppColorPicker from '../../../shared/components/AppColorPicker.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { teamCreateColors } from '../constants/teamCreateColors'
import { boardStore } from '../stores/boardStore'
import TeamProfileImagePicker from './TeamProfileImagePicker.vue'

const emit = defineEmits(['close', 'created'])
const { notify } = useToast()
const submitting = ref(false)
const imageFile = ref(null)
const imagePreviewUrl = ref('')
const form = reactive({
  name: '',
  description: '',
  color: teamCreateColors[0]?.value || ''
})

const revokePreviewUrl = () => {
  if (imagePreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(imagePreviewUrl.value)
  }
}

const selectProfileImage = file => {
  revokePreviewUrl()
  imageFile.value = file
  imagePreviewUrl.value = URL.createObjectURL(file)
}

onBeforeUnmount(revokePreviewUrl)

async function submit() {
  submitting.value = true
  try {
    let workspace = await boardStore.createWorkspace(form)
    if (imageFile.value) {
      try {
        workspace = await boardStore.uploadTeamProfileImage(
          workspace.id,
          imageFile.value,
          imagePreviewUrl.value
        )
        notify('팀 스페이스와 프로필 이미지를 등록했습니다.')
      } catch (error) {
        notify(
          error?.message ||
            '팀 스페이스는 생성했지만 프로필 이미지를 등록하지 못했습니다. 설정에서 다시 시도해 주세요.'
        )
      }
    } else {
      notify('팀 스페이스를 만들었습니다.')
    }
    emit('created', workspace)
    emit('close')
  } catch (error) {
    notify(error?.message || '팀 스페이스를 만들지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.team-profile-picker {
  margin: 18px 0;
}

.team-color-field {
  display: grid;
  gap: 14px;
  margin: 0 0 24px;
  padding: 18px;
  border: 1px solid #e1e5ed;
  border-radius: 12px;
  background: #f8f9fc;
}

.field textarea {
  resize: none;
}

.team-color-field legend {
  display: block;
  width: 100%;
  padding: 0;
}

.team-color-field legend small {
  color: #7a8394;
  font-size: 10px;
  font-weight: 400;
}
</style>
