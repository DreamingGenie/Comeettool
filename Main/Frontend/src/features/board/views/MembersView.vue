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
    <section class="member-table">
      <div class="table-tools">
        <span>
          <button
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
      <div class="member-row head">
        <span>이름</span><span>권한</span><span>팀 역할</span><span>상태</span><span>관리</span>
      </div>
      <article v-for="member in visibleMembers" :key="member.memberId" class="member-row">
        <div class="person">
          <i class="avatar">{{ member.avatarText }}</i>
          <span
            >{{ member.name }}<small>{{ member.identifier }}</small></span
          >
        </div>
        <select
          v-if="canManage && member.authority !== 'OWNER'"
          class="member-select"
          :value="member.authority"
          :disabled="pendingMemberId === member.memberId"
          @change="changeAuthority(member, $event.target.value)"
        >
          <option value="MEMBER">Member</option>
          <option value="GUEST">Guest</option>
        </select>
        <span v-else class="role">{{ member.authorityLabel }}</span>
        <select
          class="member-select"
          :value="member.teamRoleId ?? ''"
          :disabled="!canManage || pendingMemberId === member.memberId"
          @change="changeTeamRole(member, $event.target.value)"
        >
          <option value="">역할 없음</option>
          <option
            v-for="role in boardState.teamRoles"
            :key="role.teamRoleId"
            :value="role.teamRoleId"
          >
            {{ role.roleName }}
          </option>
        </select>
        <span
          class="status"
          :class="{ away: member.status === 'Away', off: member.status === 'Offline' }"
        >
          {{ member.status }}
        </span>
        <button
          v-if="canManage && member.authority !== 'OWNER'"
          class="member-kick"
          type="button"
          :disabled="pendingMemberId === member.memberId"
          @click="kickMember(member)"
        >
          강퇴
        </button>
        <span v-else class="member-owner-label">{{
          member.authority === 'OWNER' ? '소유자' : '-'
        }}</span>
      </article>
      <footer class="table-foot member-pagination">
        <span>
          Showing {{ rangeStart }}–{{ rangeEnd }} of {{ filteredMembers.length }} members
        </span>
        <nav v-if="totalPages > 1" aria-label="멤버 목록 페이지">
          <button
            type="button"
            aria-label="이전 페이지"
            :disabled="currentPage === 1"
            @click="currentPage -= 1"
          >
            ‹
          </button>
          <b>{{ currentPage }} / {{ totalPages }}</b>
          <button
            type="button"
            aria-label="다음 페이지"
            :disabled="currentPage === totalPages"
            @click="currentPage += 1"
          >
            ›
          </button>
        </nav>
      </footer>
    </section>
    <TeamRoleManager :team-id="teamId" :roles="boardState.teamRoles" :can-manage="canManage" />
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
const query = ref('')
const reverse = ref(false)
const authorityFilter = ref('ALL')
const showInvite = ref(false)
const pendingMemberId = ref('')
const currentPage = ref(1)
const pageSize = 4
const filteredMembers = computed(() => {
  const keyword = query.value.toLowerCase()
  const rows = boardState.members.filter((member) => {
    const matchesKeyword = Object.values(member).join(' ').toLowerCase().includes(keyword)
    const matchesAuthority =
      authorityFilter.value === 'ALL' || member.authority === authorityFilter.value
    return matchesKeyword && matchesAuthority
  })
  return reverse.value ? [...rows].reverse() : rows
})
const canManage = computed(() => String(boardState.team.role || '').toUpperCase() === 'OWNER')
const totalPages = computed(() => Math.max(1, Math.ceil(filteredMembers.value.length / pageSize)))
const visibleMembers = computed(() => {
  const offset = (currentPage.value - 1) * pageSize
  return filteredMembers.value.slice(offset, offset + pageSize)
})
const rangeStart = computed(() =>
  filteredMembers.value.length ? (currentPage.value - 1) * pageSize + 1 : 0
)
const rangeEnd = computed(() =>
  Math.min(currentPage.value * pageSize, filteredMembers.value.length)
)

watch([query, reverse, authorityFilter], () => {
  currentPage.value = 1
})

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

function changeAuthority(member, authority) {
  if (authority === member.authority) return
  runMemberAction(
    member,
    () => boardStore.changeMemberAuthority(teamId.value, member.memberId, authority),
    `${member.name}님의 권한을 변경했습니다.`
  )
}

function changeTeamRole(member, value) {
  const teamRoleId = value ? Number(value) : null
  if (teamRoleId === member.teamRoleId) return
  runMemberAction(
    member,
    () => boardStore.assignTeamRole(teamId.value, member.memberId, teamRoleId),
    `${member.name}님의 팀 역할을 변경했습니다.`
  )
}

function kickMember(member) {
  if (!window.confirm(`${member.name}님을 팀 스페이스에서 강퇴할까요?`)) return
  runMemberAction(
    member,
    () => boardStore.kickMember(teamId.value, member.memberId),
    `${member.name}님을 팀 스페이스에서 내보냈습니다.`
  )
}

watch(totalPages, (pages) => {
  if (currentPage.value > pages) currentPage.value = pages
})
</script>

<style scoped>
.member-row {
  grid-template-columns: minmax(190px, 2fr) minmax(105px, 0.85fr) minmax(125px, 1fr) 0.7fr 70px;
  gap: 12px;
  padding: 0 28px;
}

.member-select {
  width: 100%;
  min-height: 32px;
  border: 1px solid #d5dae5;
  border-radius: 7px;
  background: #fff;
  padding: 0 8px;
  color: #35405a;
  font-size: 10px;
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

.member-pagination nav {
  display: flex;
  align-items: center;
  gap: 8px;
}

.member-pagination nav button {
  display: grid;
  width: 29px;
  height: 29px;
  padding: 0;
  place-items: center;
  border: 1px solid #d7dce7;
  border-radius: 7px;
  background: #fff;
  color: #35405a;
  font-size: 19px;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    transform 0.18s ease;
}

.member-pagination nav button:not(:disabled):hover {
  border-color: var(--blue);
  background: #eef1ff;
  color: var(--blue);
  transform: translateY(-1px);
}

.member-pagination nav button:disabled {
  cursor: default;
  opacity: 0.38;
}

.member-pagination nav b {
  min-width: 36px;
  color: #697287;
  text-align: center;
  font-size: 11px;
}

@media (max-width: 900px) {
  .member-table {
    overflow-x: auto;
  }

  .member-row,
  .table-tools,
  .table-foot {
    min-width: 760px;
  }
}
</style>
