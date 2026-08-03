<template>
  <TeamLayout active-section="members" :retry="reloadBoard">
    <header class="page-heading">
      <div>
        <span class="eyebrow">TEAM MEMBER</span>
        <h1>팀 멤버</h1>
        <p>팀 멤버를 초대하고 권한을 관리하세요.</p>
      </div>
      <button class="primary" type="button" @click="showInvite = true">＋ 멤버 초대</button>
    </header>
    <nav class="member-tabs" aria-label="멤버 관리 메뉴">
      <button type="button" :class="{ active: activeTab === 'all' }" @click="activeTab = 'all'">
        전체
      </button>
      <button
        type="button"
        :class="{ active: activeTab === 'authority' }"
        @click="activeTab = 'authority'"
      >
        멤버 역할
      </button>
      <button type="button" :class="{ active: activeTab === 'job' }" @click="activeTab = 'job'">
        직무
      </button>
    </nav>

    <section class="member-table">
      <div class="table-tools">
        <span>
          <button
            v-if="activeTab === 'authority'"
            type="button"
            @click="
              authorityFilter =
                authorityFilter === 'ALL'
                  ? 'MEMBER'
                  : authorityFilter === 'MEMBER'
                    ? 'GUEST'
                    : 'ALL'
            "
          >
            권한: {{ authorityFilter === 'ALL' ? '전체' : authorityFilter }}
          </button>
          <button type="button" @click="reverse = !reverse">☰ 정렬</button>
        </span>
        <input v-model.trim="query" placeholder="⌕　 멤버를 찾아보세요..." />
      </div>

      <div v-if="activeTab === 'authority'" class="member-row authority-row head">
        <span>멤버</span><span>역할</span><span>관리</span>
      </div>
      <div v-else-if="activeTab === 'job'" class="member-row job-row head">
        <span>멤버</span><span>직무</span>
      </div>
      <div v-else class="member-row all-row head">
        <span>멤버</span><span>역할</span><span>직무</span><span>관리</span>
      </div>

      <article
        v-for="member in visibleMembers"
        :key="`${activeTab}-${member.memberId}`"
        class="member-row"
        :class="`${activeTab}-row`"
      >
        <div class="person">
          <i class="avatar">{{ member.avatarText }}</i>
          <span>{{ member.name }}</span>
        </div>

        <template v-if="activeTab === 'authority'">
          <AppSelect
            v-model="authorityDrafts[member.memberId]"
            class="member-select"
            :options="authorityOptions"
            aria-label="멤버 권한"
            :disabled="!canManage || member.authority === 'OWNER' || savingAuthority"
          />
        </template>

        <template v-else-if="activeTab === 'job'">
          <AppSelect
            v-model="jobDrafts[member.memberId]"
            class="member-select"
            :options="teamRoleOptions"
            aria-label="멤버 직무"
            :disabled="!canManage || savingJobs || !boardState.teamRoles.length"
          />
        </template>

        <template v-else>
          <span class="member-value authority-value">{{ member.authorityLabel }}</span>
          <span class="member-value job-value" :style="jobStyle(member.teamRoleId)">
            {{ jobName(member.teamRoleId) }}
          </span>
        </template>

        <span v-if="activeTab === 'all'" class="member-actions">
          <button
            v-if="canManage && member.authority !== 'OWNER'"
            class="member-kick"
            type="button"
            :disabled="pendingMemberId === member.memberId"
            @click="kickMember(member)"
          >
            추방
          </button>
          <span v-else class="member-owner-label">{{
            member.authority === 'OWNER' ? '현재 소유자' : '-'
          }}</span>
        </span>

        <span v-if="activeTab === 'authority'" class="member-actions">
          <template v-if="canManage && member.authority !== 'OWNER'">
            <button
              class="member-delegate"
              type="button"
              :disabled="pendingMemberId === member.memberId || savingAuthority"
              @click="prepareDelegation(member)"
            >
              위임
            </button>
          </template>
          <span v-else class="member-owner-label">{{
            member.authority === 'OWNER' ? '현재 소유자' : '-'
          }}</span>
        </span>
      </article>

      <footer class="table-foot member-pagination">
        <span>총 {{ filteredMembers.length }}명의 멤버</span>
      </footer>

      <div v-if="canManage && activeTab !== 'all'" class="member-save-bar">
        <span v-if="activeTab === 'authority'">
          Owner 변경은 선택한 멤버에게 소유권을 위임합니다.
        </span>
        <span v-else>멤버별 직무 선택을 확인한 뒤 저장해 주세요.</span>
        <button
          v-if="activeTab === 'authority'"
          class="primary"
          type="button"
          :disabled="savingAuthority || !authorityChangeCount"
          @click="saveAuthorityChanges"
        >
          {{ savingAuthority ? '저장 중' : `역할 변경 저장 (${authorityChangeCount})` }}
        </button>
        <button
          v-else
          class="primary"
          type="button"
          :disabled="savingJobs || !jobChangeCount"
          @click="saveJobChanges"
        >
          {{ savingJobs ? '저장 중' : `직무 변경 저장 (${jobChangeCount})` }}
        </button>
      </div>
    </section>

    <TeamRoleManager
      v-if="activeTab === 'job'"
      :team-id="teamId"
      :roles="boardState.teamRoles"
      :can-manage="canManage"
    />
  </TeamLayout>
  <InviteModal
    v-if="showInvite"
    :team-id="teamId"
    :members="boardState.members"
    @close="showInvite = false"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AppSelect from '../../../shared/components/AppSelect.vue'
import { useToast } from '../../../shared/composables/useToast'
import InviteModal from '../components/InviteModal.vue'
import TeamRoleManager from '../components/TeamRoleManager.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'
import { boardStore } from '../stores/boardStore'

const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team', 'members', 'teamRoles']
})
const { notify } = useToast()
const activeTab = ref('all')
const query = ref('')
const reverse = ref(false)
const authorityFilter = ref('ALL')
const showInvite = ref(false)
const pendingMemberId = ref('')
const savingAuthority = ref(false)
const savingJobs = ref(false)
const authorityDrafts = ref({})
const jobDrafts = ref({})
const authorityOptions = [
  { value: 'OWNER', label: 'Owner' },
  { value: 'MEMBER', label: 'Member' },
  { value: 'GUEST', label: 'Guest' }
]
const teamRoleOptions = computed(() => {
  if (!boardState.teamRoles.length) {
    return [{ value: '', label: '저장된 직무가 없습니다.', disabled: true }]
  }
  return [
    { value: '', label: '직무 없음' },
    ...boardState.teamRoles.map((role) => ({
      value: String(role.teamRoleId),
      label: role.roleName
    }))
  ]
})
const filteredMembers = computed(() => {
  const keyword = query.value.toLowerCase()
  const rows = boardState.members.filter((member) => {
    const matchesKeyword = Object.values(member).join(' ').toLowerCase().includes(keyword)
    const matchesAuthority =
      activeTab.value !== 'authority' ||
      authorityFilter.value === 'ALL' ||
      member.authority === authorityFilter.value
    return matchesKeyword && matchesAuthority
  })
  return reverse.value ? [...rows].reverse() : rows
})
const canManage = computed(() => String(boardState.team.role || '').toUpperCase() === 'OWNER')
const authorityChanges = computed(() =>
  boardState.members.filter((member) => authorityDrafts.value[member.memberId] !== member.authority)
)
const jobChanges = computed(() =>
  boardState.members.filter(
    (member) => String(jobDrafts.value[member.memberId] ?? '') !== String(member.teamRoleId ?? '')
  )
)
const authorityChangeCount = computed(() => authorityChanges.value.length)
const jobChangeCount = computed(() => jobChanges.value.length)
const visibleMembers = computed(() => filteredMembers.value.slice(0, 10))

function jobName(teamRoleId) {
  if (teamRoleId === null || teamRoleId === undefined || teamRoleId === '') return '직무 없음'
  return (
    boardState.teamRoles.find((role) => String(role.teamRoleId) === String(teamRoleId))?.roleName ||
    '삭제된 직무'
  )
}

function jobStyle(teamRoleId) {
  const role = boardState.teamRoles.find((item) => String(item.teamRoleId) === String(teamRoleId))
  if (!role?.color) return {}
  return {
    backgroundColor: role.color,
    borderColor: role.color,
    color: '#fff'
  }
}

watch(
  () => boardState.members.map((member) => [member.memberId, member.authority, member.teamRoleId]),
  () => {
    authorityDrafts.value = Object.fromEntries(
      boardState.members.map((member) => [member.memberId, member.authority])
    )
    jobDrafts.value = Object.fromEntries(
      boardState.members.map((member) => [member.memberId, String(member.teamRoleId ?? '')])
    )
  },
  { immediate: true }
)

async function runMemberAction(member, action, successMessage) {
  pendingMemberId.value = member.memberId
  try {
    await action()
    notify(successMessage)
  } catch (error) {
    notify(error?.message || '멤버 요청을 처리하지 못했습니다.')
  } finally {
    pendingMemberId.value = ''
  }
}

function prepareDelegation(member) {
  authorityDrafts.value[member.memberId] = 'OWNER'
  notify(`${member.name}님을 Owner로 선택했습니다. 저장하면 소유권이 위임됩니다.`)
}

async function saveAuthorityChanges() {
  if (!canManage.value || savingAuthority.value || !authorityChanges.value.length) return
  const changes = authorityChanges.value
    .map((member) => ({
      member,
      authority: authorityDrafts.value[member.memberId]
    }))
    .sort((left, right) => Number(left.authority === 'OWNER') - Number(right.authority === 'OWNER'))
  const ownerChanges = changes.filter(({ authority }) => authority === 'OWNER')

  if (ownerChanges.length > 1) {
    notify('Owner로 위임할 멤버는 한 명만 선택해 주세요.')
    return
  }
  if (
    ownerChanges.length &&
    !window.confirm(`${ownerChanges[0].member.name}님에게 팀 소유권을 위임할까요?`)
  ) {
    return
  }

  savingAuthority.value = true
  try {
    for (const { member, authority } of changes) {
      if (authority === 'OWNER') {
        await boardStore.transferWorkspaceOwnership(teamId.value, member.userId)
      } else {
        await boardStore.changeMemberAuthority(teamId.value, member.memberId, authority)
      }
    }
    await Promise.all([boardStore.loadTeam(teamId.value), boardStore.loadMembers(teamId.value)])
    notify('멤버 역할 변경사항을 저장했습니다.')
  } catch (error) {
    notify(error?.message || '멤버 역할 변경사항을 저장하지 못했습니다.')
  } finally {
    savingAuthority.value = false
  }
}

async function saveJobChanges() {
  if (!canManage.value || savingJobs.value || !jobChanges.value.length) return
  const changes = jobChanges.value.map((member) => ({
    member,
    teamRoleId: jobDrafts.value[member.memberId] ? Number(jobDrafts.value[member.memberId]) : null
  }))

  savingJobs.value = true
  try {
    for (const { member, teamRoleId } of changes) {
      await boardStore.assignTeamRole(teamId.value, member.memberId, teamRoleId)
    }
    await boardStore.loadMembers(teamId.value)
    notify('멤버 직무 변경사항을 저장했습니다.')
  } catch (error) {
    notify(error?.message || '멤버 직무 변경사항을 저장하지 못했습니다.')
  } finally {
    savingJobs.value = false
  }
}

function kickMember(member) {
  if (!window.confirm(`${member.name}님을 팀 스페이스에서 강퇴할까요?`)) return
  runMemberAction(
    member,
    () => boardStore.kickMember(teamId.value, member.memberId),
    `${member.name}님을 팀 스페이스에서 내보냈습니다.`
  )
}

</script>

<style scoped>
.member-tabs {
  display: flex;
  width: fit-content;
  gap: 4px;
  margin: 20px 0 14px;
  padding: 4px;
  border: 1px solid #dde2ec;
  border-radius: 12px;
  background: #f0f2f7;
  font-family: 'Noto Sans KR', sans-serif;
}

.member-tabs button {
  min-width: 112px;
  min-height: 38px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #697287;
  font-size: 12px;
  font-weight: 700;
}

.member-tabs button.active {
  background: #fff;
  color: var(--blue);
  box-shadow: 0 2px 8px #27365714;
}

.member-table {
  overflow: hidden;
  border: 1px solid #dfe3ec;
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 5px 20px #25335408;
  font-family: 'Noto Sans KR', sans-serif;
}

.member-row.all-row {
  grid-template-columns:
    minmax(230px, 2fr) minmax(130px, 0.8fr) minmax(170px, 1.1fr)
    minmax(80px, 0.5fr);
  gap: 18px;
  padding: 0 30px;
}

.member-row.authority-row {
  grid-template-columns: minmax(230px, 2fr) minmax(180px, 1fr) minmax(180px, 1fr);
  gap: 18px;
  padding: 0 30px;
}

.member-row.job-row {
  grid-template-columns: minmax(230px, 2fr) minmax(240px, 1.4fr);
  gap: 18px;
  padding: 0 30px;
}

.member-row:not(.head) {
  min-height: 78px;
}

.member-row.head {
  min-height: 44px;
  background: #fafbfc;
  color: #788196;
  font-size: 12px;
  font-weight: 700;
}

.member-select {
  width: 100%;
  font-size: 13px;
}

.member-value {
  display: inline-flex;
  width: fit-content;
  min-width: 76px;
  min-height: 29px;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  padding: 0 12px;
  font-size: 10px;
  font-weight: 700;
}

.authority-value {
  background: #eef1ff;
  color: #566ee1;
}

.job-value {
  border: 1px solid #dde5e2;
  background: #f1f5f4;
  color: #557568;
}

.member-select:disabled {
  background: #f4f5f8;
  color: #7d8595;
}

.member-kick {
  min-height: 30px;
  border: 1px solid #efcbd1;
  border-radius: 7px;
  background: #fff7f8;
  color: #c14455;
  font-size: 10px;
  font-weight: 700;
}

.member-delegate {
  min-height: 30px;
  border: 1px solid #cbd4f3;
  border-radius: 7px;
  background: #f4f6ff;
  color: #536bdd;
  font-size: 10px;
  font-weight: 700;
}

.member-delegate:hover:not(:disabled) {
  background: #e9edff;
}

.member-actions {
  display: flex;
  justify-content: flex-start;
  gap: 6px;
}

.member-actions button {
  min-width: 52px;
}

.member-kick:hover:not(:disabled) {
  background: #ffecee;
}

.member-owner-label {
  color: #8a92a2;
  font-size: 10px;
  text-align: center;
}

.member-pagination {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.member-save-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 18px;
  padding: 16px 24px;
  border-top: 1px solid #e2e5ed;
  background: #f8f9fc;
}

.member-save-bar span {
  color: #747d90;
  font-size: 10px;
}

.member-save-bar button {
  min-width: 150px;
}

@media (max-width: 900px) {
  .member-table {
    overflow-x: auto;
  }

  .member-row,
  .table-tools,
  .table-foot {
    min-width: 700px;
  }
}
</style>
