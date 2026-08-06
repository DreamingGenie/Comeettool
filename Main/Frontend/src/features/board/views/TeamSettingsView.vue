<template>
  <TeamLayout active-section="settings" :retry="reloadBoard">
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
        <header class="basic-settings-header">
          <i>
            <img v-if="form.profileImage" :src="form.profileImage" alt="" />
            <span v-else>{{ boardState.team.badge }}</span>
          </i>
          <div><h2>기본 정보</h2><p>팀 멤버에게 표시되는 정보입니다.</p></div>
          <TeamProfileImagePicker
            compact
            :model-value="form.profileImage"
            :fallback="boardState.team.badge"
            :color="form.color"
            label="팀 프로필 이미지"
            @file-change="selectProfileImage"
            @error="notify"
          />
        </header>
        <label class="field">팀 스페이스 이름<input v-model.trim="form.name" /></label>
        <label class="field">팀 설명<textarea v-model.trim="form.description"></textarea></label>
        <fieldset class="team-color-field">
          <legend>
            <b>팀 대표 색상</b><br />
            <small>팀 아이콘과 포인트 색상에 사용됩니다.</small>
          </legend>
          <AppColorPicker
            v-model="form.color"
            :options="teamCreateColors"
            subject="팀"
          />
        </fieldset>
      </section>
      <section class="settings-card">
        <header>
          <i class="access-icon">
            <img src="/assets/icons/identification.svg" alt="" aria-hidden="true" />
          </i>
          <div><h2>접근 및 권한</h2><p>새 멤버의 기본 접근 수준을 설정합니다.</p></div>
        </header>
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
          <AppSelect
            v-model="form.defaultMemberRole"
            :options="defaultMemberRoleOptions"
            aria-label="신규 멤버 기본 역할"
          />
        </label>
      </section>
      <section class="settings-card wide">
        <header>
          <i class="notification-icon">
            <img src="/assets/icons/bell.svg" alt="" aria-hidden="true" />
          </i>
          <div><h2>알림 설정</h2><p>팀 전체에 적용되는 기본 알림입니다.</p></div>
        </header>
        <div class="notification-grid">
          <label><input v-model="form.notifications.meetingReminder" type="checkbox" /> 회의 시작 10분 전 알림</label>
          <label><input v-model="form.notifications.documentUpdates" type="checkbox" /> 새 문서 및 댓글 알림</label>
          <label><input v-model="form.notifications.aiSummary" type="checkbox" /> AI 요약 생성 완료 알림</label>
          <label><input v-model="form.notifications.weeklyReport" type="checkbox" /> 주간 활동 리포트</label>
        </div>
      </section>
      <section class="settings-card danger wide">
        <header>
          <i>!</i>
          <div>
            <h2>위험 영역</h2>
            <p>소유권 변경과 스페이스 삭제·나가기는 신중하게 진행하세요.</p>
          </div>
        </header>
        <div v-if="isOwner" class="ownership-transfer">
          <div>
            <b>소유권 위임</b>
            <small>다른 멤버에게 Owner 권한을 넘기면 내 권한은 Member로 변경됩니다.</small>
          </div>
          <div v-if="eligibleOwners.length" class="ownership-controls">
            <AppSelect
              v-model="selectedOwnerId"
              class="ownership-select"
              :options="ownerOptions"
              placeholder="새 Owner를 선택하세요"
              aria-label="새 스페이스 소유자"
            />
            <button
              class="outline-btn"
              type="button"
              :disabled="!selectedOwnerId || actionPending"
              @click="openConfirmation('transfer')"
            >
              소유권 위임
            </button>
          </div>
          <small v-else class="ownership-empty">
            소유권을 넘길 다른 멤버가 없습니다.
          </small>
        </div>
        <div class="danger-action">
          <span>
            <b>{{ isOwner ? '팀 스페이스 삭제' : '팀 스페이스 나가기' }}</b>
            <small>
              {{
                isOwner
                  ? '하위 문서와 회의 정보를 포함해 스페이스를 삭제합니다.'
                  : '스페이스에서 내 멤버십을 제거합니다.'
              }}
            </small>
          </span>
          <button
            class="danger-btn"
            type="button"
            :disabled="actionPending"
            @click="openConfirmation(isOwner ? 'delete' : 'leave')"
          >
            {{ isOwner ? '팀 스페이스 삭제' : '팀 스페이스 나가기' }}
          </button>
        </div>
      </section>
    </div>
  </TeamLayout>
  <BaseModal v-if="pendingAction" @close="closeConfirmation">
    <div class="space-confirmation">
      <small class="eyebrow">SPACE CONFIRMATION</small>
      <h2>{{ confirmationCopy.title }}</h2>
      <p>{{ confirmationCopy.description }}</p>
      <div class="space-confirmation-actions">
        <button
          class="outline-btn"
          type="button"
          :disabled="actionPending"
          @click="closeConfirmation"
        >
          취소
        </button>
        <button
          :class="pendingAction === 'transfer' ? 'primary' : 'danger-confirm'"
          type="button"
          :disabled="actionPending"
          @click="confirmAction"
        >
          {{ actionPending ? '처리 중...' : confirmationCopy.confirmLabel }}
        </button>
      </div>
    </div>
  </BaseModal>
</template>

<script setup>
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import AppColorPicker from '../../../shared/components/AppColorPicker.vue'
import AppSelect from '../../../shared/components/AppSelect.vue'
import BaseModal from '../../../shared/components/BaseModal.vue'
import { useToast } from '../../../shared/composables/useToast'
import TeamLayout from '../components/TeamLayout.vue'
import TeamProfileImagePicker from '../components/TeamProfileImagePicker.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { teamCreateColors } from '../constants/teamCreateColors'
import { boardStore } from '../stores/boardStore'

const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team']
})
const router = useRouter()
const { notify } = useToast()
const saving = ref(false)
const actionPending = ref(false)
const pendingAction = ref('')
const selectedOwnerId = ref('')
const imageFile = ref(null)
const imagePreviewUrl = ref('')
const originalProfileImage = ref('')
const isOwner = computed(
  () => String(boardState.team.role || '').toLowerCase() === 'owner'
)
const eligibleOwners = computed(() =>
  (boardState.team.members || []).filter(
    member =>
      String(member.userId) !== String(boardState.team.ownerId) &&
      member.authority !== 'OWNER'
  )
)
const defaultMemberRoleOptions = computed(() =>
  (boardState.team.defaultMemberRoles || []).map((role) => ({ value: role, label: role }))
)
const ownerOptions = computed(() =>
  eligibleOwners.value.map((member) => ({
    value: String(member.userId),
    label: `${member.name} · ${member.authorityLabel}`
  }))
)
const selectedOwner = computed(() =>
  eligibleOwners.value.find(
    member => String(member.userId) === selectedOwnerId.value
  )
)
const confirmationCopy = computed(() => {
  if (pendingAction.value === 'transfer') {
    return {
      title: `${selectedOwner.value?.name || '선택한 멤버'}님에게 소유권을 넘길까요?`,
      description:
        '완료 후 선택한 멤버가 Owner가 되고 내 권한은 Member로 변경됩니다.',
      confirmLabel: '소유권 위임'
    }
  }
  if (pendingAction.value === 'delete') {
    return {
      title: '팀 스페이스를 삭제할까요?',
      description:
        '삭제한 스페이스와 하위 데이터는 되돌릴 수 없습니다. Owner만 실행할 수 있습니다.',
      confirmLabel: '삭제'
    }
  }
  return {
    title: '팀 스페이스에서 나갈까요?',
    description:
      '나간 뒤에는 다시 초대받기 전까지 이 스페이스에 접근할 수 없습니다.',
    confirmLabel: '나가기'
  }
})
const form = reactive({
  name: '',
  description: '',
  color: '',
  profileImage: '',
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
    if (!imageFile.value) {
      originalProfileImage.value = team.profileImage || ''
    }
    Object.assign(form, team, {
      profileImage: imageFile.value ? form.profileImage : team.profileImage || '',
      notifications: { ...form.notifications, ...(team.notifications || {}) }
    })
  },
  { immediate: true }
)

const revokePreviewUrl = () => {
  if (imagePreviewUrl.value?.startsWith('blob:')) {
    URL.revokeObjectURL(imagePreviewUrl.value)
  }
  imagePreviewUrl.value = ''
}

const selectProfileImage = file => {
  revokePreviewUrl()
  imageFile.value = file
  imagePreviewUrl.value = URL.createObjectURL(file)
  form.profileImage = imagePreviewUrl.value
  boardStore.previewTeamProfileImage(teamId.value, imagePreviewUrl.value)
}

onBeforeUnmount(() => {
  if (imageFile.value) {
    boardStore.previewTeamProfileImage(teamId.value, originalProfileImage.value)
  }
  revokePreviewUrl()
})

function openConfirmation(action) {
  if (action === 'transfer' && !selectedOwnerId.value) {
    notify('소유권을 넘길 멤버를 선택해 주세요.')
    return
  }
  pendingAction.value = action
}

function closeConfirmation() {
  if (!actionPending.value) pendingAction.value = ''
}

async function confirmAction() {
  actionPending.value = true
  try {
    if (pendingAction.value === 'transfer') {
      await boardStore.transferWorkspaceOwnership(
        teamId.value,
        selectedOwnerId.value
      )
      selectedOwnerId.value = ''
      notify('스페이스 소유권을 위임했습니다.')
    } else if (pendingAction.value === 'delete') {
      await boardStore.deleteWorkspace(teamId.value)
      notify('팀 스페이스를 삭제했습니다.')
      await router.replace('/home')
    } else {
      await boardStore.leaveWorkspace(teamId.value)
      notify('팀 스페이스에서 나갔습니다.')
      await router.replace('/home')
    }
    pendingAction.value = ''
  } catch (error) {
    notify(error?.message || '스페이스 요청을 처리하지 못했습니다.')
  } finally {
    actionPending.value = false
  }
}

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
    if (imageFile.value) {
      try {
        const updatedTeam = await boardStore.uploadTeamProfileImage(
          teamId.value,
          imageFile.value,
          imagePreviewUrl.value
        )
        const uploadedImage = updatedTeam.profileImage || ''
        imageFile.value = null
        originalProfileImage.value = uploadedImage
        form.profileImage = uploadedImage
        revokePreviewUrl()
      } catch (error) {
        notify(
          error?.message ||
            '기본 설정은 저장했지만 팀 프로필 이미지를 등록하지 못했습니다. 다시 시도해 주세요.'
        )
        return
      }
    }
    notify('팀 설정을 저장했습니다.')
  } catch (error) {
    notify(error?.message || '팀 설정을 저장하지 못했습니다.')
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.basic-settings-header > i {
  display: grid;
  flex: 0 0 42px;
  place-items: center;
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 11px;
}

.basic-settings-header > i img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.basic-settings-header {
  align-items: center;
  min-height: 66px;
}

.basic-settings-header > div {
  min-width: 0;
}

.notification-icon img,
.access-icon img {
  display: block;
  width: 17px;
  height: 17px;
  object-fit: contain;
  filter: brightness(0) saturate(100%) invert(42%) sepia(79%) saturate(1747%)
    hue-rotate(211deg) brightness(98%) contrast(88%);
}

.team-color-field {
  display: grid;
  gap: 12px;
  margin: 0;
  padding: 14px;
  border: 1px solid #e1e5ed;
  border-radius: 10px;
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

.ownership-transfer,
.danger-action {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 14px 0;
}

.ownership-transfer {
  border-bottom: 1px solid #f0dede;
}

.ownership-transfer > div:first-child,
.danger-action > span {
  display: grid;
  gap: 4px;
}

.ownership-transfer small,
.danger-action small,
.ownership-empty {
  color: #7b8390;
  font-size: 10px;
}

.ownership-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ownership-select {
  min-width: 210px;
}

.ownership-empty {
  margin-left: auto;
}

.danger-btn:disabled,
.danger-confirm:disabled {
  cursor: default;
  opacity: 0.55;
}

.space-confirmation {
  display: grid;
  gap: 12px;
}

.space-confirmation h2,
.space-confirmation p {
  margin: 0;
}

.space-confirmation p {
  color: #6f788a;
  line-height: 1.6;
}

.space-confirmation-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.danger-confirm {
  border: 1px solid #c92d2d;
  border-radius: 7px;
  padding: 8px 14px;
  background: #c92d2d;
  color: #fff;
  font-weight: 700;
}

@media (max-width: 720px) {
  .basic-settings-header {
    flex-wrap: wrap;
  }

  .ownership-transfer,
  .danger-action,
  .ownership-controls {
    align-items: stretch;
    flex-direction: column;
  }

  .ownership-select {
    width: 100%;
    min-width: 0;
  }

  .ownership-empty {
    margin-left: 0;
  }
}
</style>
