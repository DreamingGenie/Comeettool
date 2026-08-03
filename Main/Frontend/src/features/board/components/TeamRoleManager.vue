<template>
  <section class="team-role-manager">
    <header>
      <div>
        <span class="eyebrow">CUSTOM TEAM ROLES</span>
        <h2>팀 역할 관리</h2>
        <p>멤버에게 배정할 역할의 이름과 색상을 관리합니다.</p>
      </div>
      <form v-if="canManage" @submit.prevent="createRole">
        <input v-model.trim="newRoleName" maxlength="20" placeholder="새 역할 이름" required />
        <input v-model="newRoleColor" type="color" aria-label="새 역할 색상" />
        <button class="primary" type="submit" :disabled="pending">추가</button>
      </form>
    </header>

    <div v-if="!roles.length" class="role-empty">등록된 팀 역할이 없습니다.</div>
    <div v-else class="role-list">
      <article v-for="role in roles" :key="role.teamRoleId">
        <span class="role-color" :style="{ backgroundColor: role.color || '#7a88d8' }"></span>
        <template v-if="editingId === role.teamRoleId">
          <input v-model.trim="editName" maxlength="20" aria-label="역할 이름" />
          <input v-model="editColor" type="color" aria-label="역할 색상" />
          <button type="button" :disabled="pending" @click="saveRole(role)">저장</button>
          <button type="button" :disabled="pending" @click="cancelEdit">취소</button>
        </template>
        <template v-else>
          <b>{{ role.roleName }}</b>
          <small>{{ role.color || '기본 색상' }}</small>
          <span v-if="canManage" class="role-actions">
            <button type="button" @click="startEdit(role)">수정</button>
            <button class="danger" type="button" :disabled="pending" @click="removeRole(role)">
              삭제
            </button>
          </span>
        </template>
      </article>
    </div>
  </section>
</template>

<script setup>
import { ref } from 'vue'
import { useToast } from '../../../shared/composables/useToast'
import { boardStore } from '../stores/boardStore'

const props = defineProps({
  teamId: { type: String, required: true },
  roles: { type: Array, default: () => [] },
  canManage: Boolean
})

const { notify } = useToast()
const pending = ref(false)
const newRoleName = ref('')
const newRoleColor = ref('#6075e8')
const editingId = ref(null)
const editName = ref('')
const editColor = ref('#6075e8')

async function run(action, successMessage) {
  pending.value = true
  try {
    await action()
    notify(successMessage)
  } catch (error) {
    notify(error?.message || '팀 역할 요청을 처리하지 못했습니다.')
    throw error
  } finally {
    pending.value = false
  }
}

async function createRole() {
  if (!newRoleName.value || pending.value) return
  try {
    await run(
      () =>
        boardStore.createTeamRole(props.teamId, {
          roleName: newRoleName.value,
          color: newRoleColor.value
        }),
      '팀 역할을 추가했습니다.'
    )
    newRoleName.value = ''
  } catch {
    // 오류 토스트는 run에서 표시한다.
  }
}

function startEdit(role) {
  editingId.value = role.teamRoleId
  editName.value = role.roleName
  editColor.value = role.color || '#6075e8'
}

function cancelEdit() {
  editingId.value = null
}

async function saveRole(role) {
  if (!editName.value || pending.value) return
  try {
    await run(
      () =>
        boardStore.updateTeamRole(props.teamId, role.teamRoleId, {
          roleName: editName.value,
          color: editColor.value
        }),
      '팀 역할을 수정했습니다.'
    )
    cancelEdit()
  } catch {
    // 오류 토스트는 run에서 표시한다.
  }
}

async function removeRole(role) {
  if (pending.value || !window.confirm(`${role.roleName} 역할을 삭제할까요?`)) return
  try {
    await run(
      () => boardStore.deleteTeamRole(props.teamId, role.teamRoleId),
      '팀 역할을 삭제했습니다.'
    )
  } catch {
    // 오류 토스트는 run에서 표시한다.
  }
}
</script>

<style scoped>
.team-role-manager {
  margin-top: 18px;
  padding: 20px;
  border: 1px solid #d7dce7;
  border-radius: 12px;
  background: #fff;
}

.team-role-manager > header {
  display: flex;
  align-items: end;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 16px;
}

.team-role-manager h2 {
  margin: 4px 0;
  font-size: 18px;
}

.team-role-manager p {
  margin: 0;
  color: #737c90;
  font-size: 11px;
}

.team-role-manager form {
  display: flex;
  gap: 7px;
}

.team-role-manager input {
  min-height: 36px;
  border: 1px solid #d7dce7;
  border-radius: 7px;
  padding: 0 10px;
}

.team-role-manager input[type='color'] {
  width: 40px;
  padding: 4px;
}

.role-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 8px;
}

.role-list article {
  min-height: 52px;
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  border: 1px solid #e1e5ee;
  border-radius: 9px;
}

.role-color {
  width: 12px;
  height: 12px;
  flex: 0 0 12px;
  border-radius: 50%;
}

.role-list b {
  font-size: 12px;
}

.role-list small {
  color: #8a92a2;
  font-size: 9px;
}

.role-actions {
  display: flex;
  gap: 4px;
  margin-left: auto;
}

.role-list button {
  padding: 5px 8px;
  border: 1px solid #d7dce7;
  border-radius: 6px;
  background: #fff;
  color: #566176;
  font-size: 9px;
}

.role-list button.danger {
  color: #c14455;
}

.role-empty {
  padding: 22px;
  border-radius: 9px;
  background: #f6f7fa;
  color: #7b8495;
  text-align: center;
  font-size: 11px;
}

@media (max-width: 760px) {
  .team-role-manager > header,
  .team-role-manager form {
    align-items: stretch;
    flex-direction: column;
  }
}
</style>
