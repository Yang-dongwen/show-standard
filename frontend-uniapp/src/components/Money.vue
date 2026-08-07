<template>
  <text :class="['money', { primary }]">{{ prefix }}{{ display }}</text>
</template>

<script setup>
import { computed } from 'vue'
import { money } from '@/utils/format'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  prefix: { type: String, default: '¥' },
  primary: { type: Boolean, default: false },
})

const display = computed(() => money(props.value))
</script>

<style scoped lang="scss">
.money {
  font-variant-numeric: tabular-nums;
  font-weight: 600;
  color: #0f172a;
}
.money.primary {
  color: #5b5ce2;
}
</style>
