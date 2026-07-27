<template>
  <div class="modal-backdrop" @click.self="$emit('close')">
    <section class="modal" :class="modalClass">
      <button class="close" type="button" aria-label="닫기" @click="$emit('close')">×</button>
      <slot></slot>
    </section>
  </div>
</template>

<script setup>
defineProps({
  modalClass: { type: String, default: '' }
})

defineEmits(['close'])
</script>
