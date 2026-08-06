<template>
  <div class="empty-hint">
    <el-empty :description="description" :image-size="imageSize">
      <el-button v-if="actionText" type="primary" @click="$emit('action')">
        {{ actionText }}
      </el-button>
    </el-empty>
  </div>
</template>

<script setup>
defineProps({
  description: { type: String, default: '暂无数据' },
  actionText: { type: String, default: '' },
  imageSize: { type: Number, default: 90 }
})
defineEmits(['action'])
</script>

<style scoped>
.empty-hint {
  padding: 24px 12px;
}
</style>
