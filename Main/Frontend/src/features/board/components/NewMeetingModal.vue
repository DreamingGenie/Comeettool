<template>
  <BaseModal @close="$emit('close')">
    <form @submit.prevent="submit">
      <small style="color:#4285ef">NEW MEETING</small>
      <h2>회의 생성하기</h2>
      <label class="field">
        회의 이름
        <input v-model.trim="form.name" required />
      </label>
      <label class="field">
        담당 팀
        <select v-model="form.teamId">
          <option v-for="workspace in workspaces" :key="workspace.id" :value="workspace.id">
            {{ workspace.name }}
          </option>
        </select>
      </label>
      <div style="height:84px"></div>
      <button class="primary block" :disabled="submitting">회의 만들기</button>
    </form>
  </BaseModal>
</template>

<script setup>
import { reactive, ref, watchEffect } from 'vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { boardStore } from '../stores/boardStore'

const props = defineProps({
  workspaces: { type: Array, default: () => [] },
  teamId: { type: String, default: '' }
})

const emit = defineEmits(['close', 'created'])
const { notify } = useToast()
const submitting = ref(false)
const form = reactive({ name: '', teamId: props.teamId })

watchEffect(() => {
  if (!form.teamId) form.teamId = props.teamId || props.workspaces[0]?.id || ''
})

async function submit() {
  submitting.value = true
  try {
    const meeting = await boardStore.createMeeting({
      teamId: form.teamId,
      name: form.name,
      title: form.name,
      roomTitle: form.name
    })
    notify('회의를 만들었습니다.')
    emit('created', meeting)
    emit('close')
  } catch (error) {
    notify(error?.message || '회의를 만들지 못했습니다.')
  } finally {
    submitting.value = false
  }
}
</script>
