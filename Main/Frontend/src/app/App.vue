<template>
  <RouterView />
  <AppToast />
</template>

<script setup>
import { RouterView } from 'vue-router'
import AppToast from '../shared/components/AppToast.vue'
</script>
