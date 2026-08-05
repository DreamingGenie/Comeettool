<template>
  <BaseModal @close="$emit('close')">
    <form @submit.prevent="submit">
      <small style="color:#4285ef">NEW TEAM SPACE</small>
      <h2>새 팀 스페이스</h2>
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
import { reactive, ref } from 'vue'
import AppColorPicker from '../../../shared/components/AppColorPicker.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { teamCreateColors } from '../constants/teamCreateColors'
import { boardStore } from '../stores/boardStore'

const emit = defineEmits(['close', 'created'])
const { notify } = useToast()
const submitting = ref(false)
const form = reactive({
  name: '',
  description: '',
  color: teamCreateColors[0]?.value || ''
})

async function submit() {
  submitting.value = true
  try {
    const workspace = await boardStore.createWorkspace(form)
    notify('팀 스페이스를 만들었습니다.')
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
.team-color-field {
  display: grid;
  gap: 14px;
  margin: 0 0 24px;
  padding: 18px;
  border: 1px solid #e1e5ed;
  border-radius: 12px;
  background: #f8f9fc;
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
