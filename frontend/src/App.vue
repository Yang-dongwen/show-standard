<template>
  <div class="app-container">
    <router-view />
  </div>
</template>

<script setup>
</script>

<style scoped>
.app-container {
  width: 100%;
  height: 100%;
  min-height: 100vh;
  overflow: hidden;
  background: var(--bg-page, #f4f6fb);
}
</style>
