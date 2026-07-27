<template>
  <AppShell :user="userState.profile || {}">
    <template #navigation>
      <TeamDock
        :workspaces="boardState.workspaces"
        :active-team-id="teamId"
        @select="selectTeam"
        @create="showNewTeam = true"
        @profile="$router.push('/profile')"
      />
      <TeamSidebar
        :team="boardState.team"
        :menu="teamMenu"
        :active-section="activeSection"
        @navigate="navigateSection"
        @home="$router.push('/home')"
      />
    </template>
    <AsyncState
      v-if="boardState.loading || boardState.error"
      :loading="boardState.loading"
      :error="boardState.error"
      :retry="reload"
    />
    <slot v-else></slot>
  </AppShell>
  <NewTeamModal v-if="showNewTeam" @close="showNewTeam = false" />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppShell from '../../../shared/components/AppShell.vue'
import AsyncState from '../../../shared/components/AsyncState.vue'
import TeamDock from '../../../shared/components/TeamDock.vue'
import TeamSidebar from '../../../shared/components/TeamSidebar.vue'
import { teamMenu } from '../constants/teamMenu'
import { useUserPage } from '../../user/composables/useUserPage'
import { boardStore } from '../stores/boardStore'
import NewTeamModal from './NewTeamModal.vue'

defineProps({
  activeSection: { type: String, required: true }
})

const router = useRouter()
const route = useRoute()
const showNewTeam = ref(false)
const boardState = boardStore.state
const teamId = computed(() => String(route.params.teamId || boardState.team.id || 'a707'))
const { userState } = useUserPage()

const selectTeam = id => router.push(`/teams/${id}/schedule`)
const navigateSection = section => router.push(`/teams/${teamId.value}/${section}`)
const reload = () =>
  boardStore.load({ teamId: teamId.value }).catch(() => undefined)
</script>
