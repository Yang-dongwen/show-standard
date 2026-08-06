<template>
  <span class="money-text" :class="toneClass">{{ display }}</span>
</template>

<script setup>
import { computed } from 'vue'
import { money } from '@/utils/format.js'

const props = defineProps({
  value: { type: [Number, String], default: 0 },
  /** auto | pos | neg | plain */
  tone: { type: String, default: 'plain' }
})

const display = computed(() => money(props.value))
const toneClass = computed(() => {
  if (props.tone === 'pos') return 'is-pos'
  if (props.tone === 'neg') return 'is-neg'
  if (props.tone === 'auto') {
    const n = Number(props.value || 0)
    return n >= 0 ? 'is-pos' : 'is-neg'
  }
  return ''
})
</script>

<style scoped>
.money-text {
  font-variant-numeric: tabular-nums;
  font-weight: 700;
  color: var(--text-primary);
}
.money-text.is-pos {
  color: #059669;
}
.money-text.is-neg {
  color: #d97706;
}
</style>
