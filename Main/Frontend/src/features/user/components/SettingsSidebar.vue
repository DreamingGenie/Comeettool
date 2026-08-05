<template>
  <aside class="side-nav settings-nav">
    <div class="search-dark">⌕　기능 검색</div>
    <small class="settings-label">설정</small>
    <nav class="settings-menu">
      <button type="button" :class="{ active: active === 'profile' }" @click="$router.push('/profile')">
        <img class="side-icon side-icon-members" src="/assets/icons/members.svg" alt="" />
        <span>프로필 변경</span>
      </button>
      <button type="button" :class="{ active: active === 'password' }" @click="$router.push('/password')">
        <img class="side-icon side-icon-lock" src="/assets/icons/lock.svg" alt="" />
        <span>비밀번호 변경</span>
      </button>
      <button type="button" :class="{ active: active === 'account' }" @click="$router.push('/account')">
        <img class="side-icon" src="/assets/icons/settings.svg" alt="" />
        <span>계정 관리</span>
      </button>
    </nav>
    <button type="button" class="back-main" @click="$router.push('/home')">← 이전으로 가기</button>
  </aside>
</template>

<script setup>
defineProps({
  active: { type: String, required: true }
})
</script>
