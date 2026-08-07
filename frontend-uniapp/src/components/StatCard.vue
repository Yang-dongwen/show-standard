<template>
  <view class="stat-card">
    <view class="stat-label">{{ label }}</view>
    <view class="stat-value" :class="{ primary }">
      <slot>{{ value }}</slot>
    </view>
    <view v-if="hint" class="stat-hint">{{ hint }}</view>
  </view>
</template>

<script setup>
defineProps({
  label: { type: String, default: '' },
  value: { type: [String, Number], default: '' },
  hint: { type: String, default: '' },
  primary: { type: Boolean, default: false },
})
</script>

<style scoped lang="scss">
.stat-card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  box-shadow: 0 4rpx 24rpx rgba(15, 23, 42, 0.06);
  min-height: 140rpx;
  box-sizing: border-box;
}
.stat-label {
  font-size: 24rpx;
  color: #64748b;
  margin-bottom: 12rpx;
}
.stat-value {
  font-size: 36rpx;
  font-weight: 700;
  color: #0f172a;
  word-break: break-all;
}
.stat-value.primary {
  color: #5b5ce2;
}
.stat-hint {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #94a3b8;
}
</style>
