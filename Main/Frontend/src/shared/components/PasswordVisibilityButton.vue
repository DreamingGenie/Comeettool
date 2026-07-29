<template>
  <button
    class="password-visibility"
    type="button"
    :aria-label="`${label} ${visible ? '숨기기' : '보기'}`"
    :title="`${label} ${visible ? '숨기기' : '보기'}`"
    @click="$emit('toggle')"
  >
    <img
      :src="visible ? '/assets/icons/eye.svg' : '/assets/icons/eye-off.svg'"
      alt=""
      aria-hidden="true"
    />
  </button>
</template>

<script setup>
defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  label: {
    type: String,
    default: '비밀번호'
  }
})

defineEmits(['toggle'])
</script>

<style scoped>
.password-visibility img {
  display: block;
  width: 18px;
  height: 18px;
  pointer-events: none;
}
</style>
