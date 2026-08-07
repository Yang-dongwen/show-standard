<template>
  <div />
</template>

<script setup>
/** 已拆分为 layouts/SaasLayout + views/*，保留空文件避免旧引用报错 */
import { onMounted } from 'vue'
import { useRouter } from 'vue-router'
const router = useRouter()
onMounted(() => router.replace('/home/dashboard'))
</script>
