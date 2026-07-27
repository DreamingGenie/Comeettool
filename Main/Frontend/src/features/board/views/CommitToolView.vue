<template>
  <div ref="legacyRoot" class="vue-app-root"></div>
  <div ref="toastRoot" class="toast" role="status" aria-live="polite"></div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { mountCommitTool } from './committool'

const props = defineProps({
  initialView: { type: String, default: 'intro' }
})

const route = useRoute()
const router = useRouter()
const legacyRoot = ref(null)
const toastRoot = ref(null)
let commitTool

const teamViews = new Set([
  'schedule',
  'members',
  'documents',
  'minutes',
  'summary',
  'feedback',
  'settings'
])

function pathForView(view) {
  if (view === 'intro') return '/'
  if (['login', 'signup', 'onboarding', 'home', 'profile', 'password'].includes(view)) {
    return `/${view}`
  }
  if (teamViews.has(view)) {
    return `/teams/${route.params.teamId || 'a707'}/${view}`
  }
  if (view === 'meeting') {
    return `/meetings/${route.params.meetingId || 'be-team-meeting'}`
  }
  return '/'
}

function navigate(view) {
  const target = pathForView(view)
  if (route.path !== target) router.push(target)
}

onMounted(() => {
  commitTool = mountCommitTool(legacyRoot.value, toastRoot.value, {
    initialView: props.initialView,
    onNavigate: navigate
  })
})

watch(
  () => props.initialView,
  view => commitTool?.setView(view)
)

onBeforeUnmount(() => commitTool?.destroy())
</script>
