<template>
  <TeamLayout active-section="members">
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
      <footer class="table-foot">
        Showing {{ visibleMembers.length }} of {{ boardState.team.memberCount }} members
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
import { computed, ref } from 'vue'
import { useToast } from '../../../shared/composables/useToast'
import InviteModal from '../components/InviteModal.vue'
import TeamLayout from '../components/TeamLayout.vue'
import { useBoardPage } from '../composables/useBoardPage'

const { boardState, teamId } = useBoardPage()
const { notify } = useToast()
const query = ref('')
const reverse = ref(false)
const showInvite = ref(false)
const visibleMembers = computed(() => {
  const keyword = query.value.toLowerCase()
  const rows = boardState.members.filter(row => row.join(' ').toLowerCase().includes(keyword))
  return reverse.value ? [...rows].reverse() : rows
})
</script>
