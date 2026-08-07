<template>
  <view class="empty-wrap">
    <view class="empty-icon">{{ icon }}</view>
    <text class="empty-text">{{ text }}</text>
    <slot />
  </view>
</template>

<script setup>
defineProps({
  text: { type: String, default: '暂无数据' },
  icon: { type: String, default: '📭' },
})
</script>

<style scoped lang="scss">
.empty-wrap {
  padding: 80rpx 40rpx;
  text-align: center;
}
.empty-icon {
  font-size: 64rpx;
  margin-bottom: 16rpx;
}
.empty-text {
  font-size: 28rpx;
  color: #94a3b8;
}
</style>
