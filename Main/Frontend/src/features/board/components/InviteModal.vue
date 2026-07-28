<template>
  <BaseModal modal-class="invite-modal" @close="$emit('close')">
    <h2>멤버 초대 및 공유</h2>
    <p>프로젝트 팀원을 초대하세요.</p>
    <form class="invite-row" @submit.prevent="invite">
      <input v-model.trim="email" type="email" placeholder="email@example.com" required />
      <select v-model="permission">
        <option value="MEMBER">편집 가능</option>
        <option value="GUEST">보기 전용</option>
      </select>
      <button class="primary" :disabled="submitting">초대 발송 →</button>
    </form>
    <div class="invite-list">
      <b>현재 멤버 {{ members.length }}</b>
      <article v-for="member in members" :key="member.id">
        <i class="avatar">{{ member.avatarText }}</i>
        <span>{{ member.name }}<br /><small>{{ member.email }}</small></span>
        <em>{{ member.role }}</em>
      </article>
    </div>
    <button class="outline-btn block" type="button" @click="copyLink">▣ 링크 복사</button>
  </BaseModal>
</template>

<script setup>
import { ref } from 'vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import { boardStore } from '../stores/boardStore'

const props = defineProps({
  teamId: { type: String, required: true },
  members: { type: Array, default: () => [] }
})

defineEmits(['close'])

const { notify } = useToast()
const email = ref('')
const permission = ref('MEMBER')
const submitting = ref(false)

async function invite() {
  submitting.value = true
  try {
    await boardStore.inviteMember(props.teamId, {
      email: email.value,
      permission: permission.value
    })
    notify(`${email.value} 주소로 초대를 보냈습니다.`)
    email.value = ''
  } catch (error) {
    notify(error?.message || '초대를 보내지 못했습니다.')
  } finally {
    submitting.value = false
  }
}

async function copyLink() {
  try {
    await navigator.clipboard.writeText(window.location.href)
    notify('초대 링크를 복사했습니다.')
  } catch {
    notify('초대 링크를 복사하지 못했습니다.')
  }
}
</script>
