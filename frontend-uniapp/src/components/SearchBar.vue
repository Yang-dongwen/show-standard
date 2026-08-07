<template>
  <view class="search-bar">
    <input
      class="search-input"
      type="text"
      :value="modelValue"
      :placeholder="placeholder"
      confirm-type="search"
      @input="onInput"
      @confirm="onConfirm"
    />
    <view v-if="modelValue" class="search-clear" @click="clear">清除</view>
  </view>
</template>

<script setup>
const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '搜索' },
})
const emit = defineEmits(['update:modelValue', 'search'])

function onInput(e) {
  emit('update:modelValue', e.detail.value)
}
function onConfirm() {
  emit('search', props.modelValue)
}
function clear() {
  emit('update:modelValue', '')
  emit('search', '')
}
</script>

<style scoped lang="scss">
.search-bar {
  display: flex;
  align-items: center;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 16rpx 24rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, 0.04);
  margin-bottom: 20rpx;
}
.search-input {
  flex: 1;
  font-size: 28rpx;
  height: 56rpx;
}
.search-clear {
  font-size: 24rpx;
  color: #5b5ce2;
  padding-left: 16rpx;
}
</style>
