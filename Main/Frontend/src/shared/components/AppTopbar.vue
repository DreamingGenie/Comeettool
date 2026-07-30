<template>
  <header class="topbar">
    <AppSparkles />
    <AppLogo to="/home" />
    <button class="user-pill" type="button" @click="$router.push('/profile')">
      <img
        v-if="user?.profileImage && !profileImageFailed"
        class="user-avatar"
        :src="user.profileImage"
        :alt="`${user?.nickname || '사용자'} 프로필`"
        @error="profileImageFailed = true"
      />
      <i
        v-else
        class="user-avatar"
        :style="{ backgroundColor: user?.userColor || '#5f6fe5' }"
      >
        {{ avatarLabel }}
      </i>
      {{ user?.nickname || '' }}
    </button>
  </header>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import AppLogo from './AppLogo.vue'
import AppSparkles from './AppSparkles.vue'

const props = defineProps({
  user: { type: Object, default: () => ({}) }
})

const profileImageFailed = ref(false)
const avatarLabel = computed(
  () =>
    props.user?.nickname?.trim().slice(0, 1) ||
    props.user?.avatarText ||
    ''
)

watch(
  () => props.user?.profileImage,
  () => {
    profileImageFailed.value = false
  }
)
</script>

<style scoped>
.topbar {
  position: relative;
  overflow: hidden;
}

.topbar > :not(.app-sparkles) {
  position: relative;
  z-index: 1;
}

.user-pill {
  border-color: rgba(255, 255, 255, 0.38);
  background: rgba(255, 255, 255, 0.04);
}

.user-avatar {
  width: 24.8px;
  height: 24.8px;
  flex: 0 0 24.8px;
  border: 1px solid rgba(255, 255, 255, 0.48);
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 2px 6px rgba(5, 15, 38, 0.2);
}

.user-pill i.user-avatar {
  color: #fff;
  font-size: 9.6px;
  letter-spacing: -0.2px;
}
</style>
