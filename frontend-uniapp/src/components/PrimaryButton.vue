<template>
  <button
    class="primary-btn"
    :class="{ block, ghost }"
    :disabled="disabled || loading"
    :loading="loading"
    @click="onClick"
  >
    <slot>{{ text }}</slot>
  </button>
</template>

<script setup>
defineProps({
  text: { type: String, default: '确定' },
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  block: { type: Boolean, default: true },
  ghost: { type: Boolean, default: false },
})
const emit = defineEmits(['click'])
function onClick(e) {
  emit('click', e)
}
</script>

<style scoped lang="scss">
.primary-btn {
  background: #5b5ce2;
  color: #ffffff;
  border-radius: 16rpx;
  font-size: 30rpx;
  font-weight: 500;
  padding: 4rpx 24rpx;
  border: none;
  line-height: 2.2;
}
.primary-btn.block {
  width: 100%;
}
.primary-btn.ghost {
  background: #ffffff;
  color: #5b5ce2;
  border: 2rpx solid #5b5ce2;
}
.primary-btn::after {
  border: none;
}
.primary-btn[disabled] {
  opacity: 0.55;
}
</style>
