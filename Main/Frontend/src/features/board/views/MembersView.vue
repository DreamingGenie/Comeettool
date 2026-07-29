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
          <button type="button" @click="notify('조건 필터는 API 연결 후 제공됩니다.')">☰ 조건</button>
          <button type="button" @click="reverse = !reverse">☰ 정렬</button>
        </span>
        <input v-model.trim="query" placeholder="⌕　 멤버를 찾아보세요..." />
      </div>
      <div class="member-row head">
        <span>이름</span><span>역할</span><span>상태</span><span>최근 활동</span>
      </div>
      <article v-for="member in visibleMembers" :key="member[2]" class="member-row">
        <div class="person">
          <i class="avatar">{{ member[0] }}</i>
          <span>{{ member[1] }}<small>{{ member[2] }}</small></span>
        </div>
        <span class="role">{{ member[3] }}</span>
        <span class="status" :class="{ away: member[4] === 'Away', off: member[4] === 'Offline' }">
          {{ member[4] }}
        </span>
        <span>{{ member[5] }}</span>
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
  </TeamLayout>
  <InviteModal
    v-if="showInvite"
    :team-id="teamId"
    :members="boardState.inviteMembers"
    @close="showInvite = false"
  />
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useToast } from '../../../shared/composables/useToast'
import InviteModal from '../components/InviteModal.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'

const { boardState, teamId, reloadBoard } = useBoardPage({
  resources: ['workspaces', 'team', 'members', 'inviteMembers']
})
const { notify } = useToast()
const query = ref('')
const reverse = ref(false)
const showInvite = ref(false)
const currentPage = ref(1)
const pageSize = 4
const filteredMembers = computed(() => {
  const keyword = query.value.toLowerCase()
  const rows = boardState.members.filter(row => row.join(' ').toLowerCase().includes(keyword))
  return reverse.value ? [...rows].reverse() : rows
})
const totalPages = computed(() => Math.max(1, Math.ceil(filteredMembers.value.length / pageSize)))
const visibleMembers = computed(() => {
  const offset = (currentPage.value - 1) * pageSize
  return filteredMembers.value.slice(offset, offset + pageSize)
})
const rangeStart = computed(() => filteredMembers.value.length ? (currentPage.value - 1) * pageSize + 1 : 0)
const rangeEnd = computed(() => Math.min(currentPage.value * pageSize, filteredMembers.value.length))

watch([query, reverse], () => {
  currentPage.value = 1
})

watch(totalPages, pages => {
  if (currentPage.value > pages) currentPage.value = pages
})
</script>

<style scoped>
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
  transition: border-color 0.18s ease, background-color 0.18s ease, transform 0.18s ease;
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
</style>
