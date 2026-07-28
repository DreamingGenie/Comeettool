<template>
  <TeamLayout active-section="settings">
    <header class="page-heading">
      <div>
        <span class="eyebrow">TEAM SETTINGS</span>
        <h1>팀 스페이스 설정</h1>
        <p>팀 정보, 접근 권한과 알림 정책을 관리하세요.</p>
      </div>
      <button class="primary" type="button" :disabled="saving" @click="save">변경사항 저장</button>
    </header>
    <div class="team-settings-grid">
      <section class="settings-card">
        <header><i>{{ boardState.team.badge }}</i><div><h2>기본 정보</h2><p>팀 멤버에게 표시되는 정보입니다.</p></div></header>
        <label class="field">팀 스페이스 이름<input v-model.trim="form.name" /></label>
        <label class="field">팀 설명<textarea v-model.trim="form.description"></textarea></label>
        <label class="field">
          팀 대표 색상
          <select v-model="form.color">
            <option v-for="color in boardState.team.colorOptions" :key="color">{{ color }}</option>
          </select>
        </label>
      </section>
      <section class="settings-card">
        <header><i>♙</i><div><h2>접근 및 권한</h2><p>새 멤버의 기본 접근 수준을 설정합니다.</p></div></header>
        <div class="setting-toggle">
          <span><b>초대 링크 활성화</b><small>링크를 가진 사용자가 가입할 수 있습니다.</small></span>
          <button type="button" :class="{ 'switch-on': form.inviteLinkEnabled }" @click="form.inviteLinkEnabled = !form.inviteLinkEnabled"><i></i></button>
        </div>
        <div class="setting-toggle">
          <span><b>Owner 승인 필요</b><small>새 멤버 가입 전 승인을 요청합니다.</small></span>
          <button type="button" :class="{ 'switch-on': form.ownerApprovalRequired }" @click="form.ownerApprovalRequired = !form.ownerApprovalRequired"><i></i></button>
        </div>
        <label class="field">
          신규 멤버 기본 역할
          <select v-model="form.defaultMemberRole">
            <option v-for="role in boardState.team.defaultMemberRoles" :key="role">{{ role }}</option>
          </select>
        </label>
      </section>
      <section class="settings-card wide">
        <header><i>♢</i><div><h2>알림 설정</h2><p>팀 전체에 적용되는 기본 알림입니다.</p></div></header>
        <div class="notification-grid">
          <label><input v-model="form.notifications.meetingReminder" type="checkbox" /> 회의 시작 10분 전 알림</label>
          <label><input v-model="form.notifications.documentUpdates" type="checkbox" /> 새 문서 및 댓글 알림</label>
          <label><input v-model="form.notifications.aiSummary" type="checkbox" /> AI 요약 생성 완료 알림</label>
          <label><input v-model="form.notifications.weeklyReport" type="checkbox" /> 주간 활동 리포트</label>
        </div>
      </section>
      <section class="settings-card danger wide">
        <header><i>!</i><div><h2>위험 영역</h2><p>이 작업은 되돌릴 수 없으니 주의하세요.</p></div><button class="danger-btn" type="button" @click="notify('팀 삭제는 관리자 확인 후 진행됩니다.')">팀 스페이스 삭제</button></header>
      </section>
    </div>
  </TeamLayout>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { useToast } from '../../../shared/composables/useToast'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const { boardState, teamId } = useBoardPage()
const { notify } = useToast()
const saving = ref(false)
const form = reactive({
  name: '',
  description: '',
  color: '',
  defaultMemberRole: '',
  inviteLinkEnabled: true,
  ownerApprovalRequired: false,
  notifications: {
    meetingReminder: true,
    documentUpdates: true,
    aiSummary: true,
    weeklyReport: false
  }
})

watch(
  () => boardState.team,
  team => {
    if (!team?.id) return
    Object.assign(form, team, {
      notifications: { ...form.notifications, ...(team.notifications || {}) }
    })
  },
  { immediate: true }
)

async function save() {
  saving.value = true
  try {
    await boardStore.updateTeam(teamId.value, {
      name: form.name,
      description: form.description,
      color: form.color,
      defaultMemberRole: form.defaultMemberRole,
      inviteLinkEnabled: form.inviteLinkEnabled,
      ownerApprovalRequired: form.ownerApprovalRequired,
      notifications: { ...form.notifications }
    })
    notify('팀 설정을 저장했습니다.')
  } catch (error) {
    notify(error?.message || '팀 설정을 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}
</script>
