<template>
  <div class="trend">
    <div v-if="!rows.length" class="empty">近 7 日暂无流水</div>
    <div v-else class="rows">
      <div v-for="row in rows" :key="row.day" class="row">
        <div class="day">{{ shortDay(row.day) }}</div>
        <div class="bars">
          <div class="bar-track">
            <div
              class="bar recharge"
              :style="{ width: pct(row.recharge, max) + '%' }"
              :title="'充值 ' + money(row.recharge)"
            />
          </div>
          <div class="bar-track">
            <div
              class="bar consume"
              :style="{ width: pct(row.consume, max) + '%' }"
              :title="'消费 ' + money(row.consume)"
            />
          </div>
        </div>
        <div class="vals">
          <span class="r">{{ money(row.recharge) }}</span>
          <span class="c">{{ money(row.consume) }}</span>
        </div>
      </div>
    </div>
    <div v-if="rows.length" class="legend">
      <span><i class="dot r" />充值</span>
      <span><i class="dot c" />消费</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { money } from '@/utils/format.js'

const props = defineProps({
  data: { type: Array, default: () => [] }
})

const rows = computed(() => props.data || [])

const max = computed(() => {
  let m = 0
  for (const r of rows.value) {
    m = Math.max(m, Number(r.recharge || 0), Number(r.consume || 0))
  }
  return m || 1
})

function pct(v, m) {
  const n = Number(v || 0)
  if (!m) return 0
  return Math.max(2, Math.round((n / m) * 100))
}

function shortDay(day) {
  if (!day) return ''
  const s = String(day)
  return s.length >= 10 ? s.slice(5) : s
}
</script>

<style scoped>
.trend { min-height: 180px; }
.empty {
  padding: 48px 0;
  text-align: center;
  color: var(--saas-text-3);
  font-size: 13px;
}
.rows { display: flex; flex-direction: column; gap: 10px; }
.row {
  display: grid;
  grid-template-columns: 44px 1fr 88px;
  gap: 10px;
  align-items: center;
}
.day {
  font-size: 12px;
  color: var(--saas-text-2);
  font-variant-numeric: tabular-nums;
}
.bars { display: flex; flex-direction: column; gap: 4px; }
.bar-track {
  height: 8px;
  background: #f1f5f9;
  border-radius: 999px;
  overflow: hidden;
}
.bar {
  height: 100%;
  border-radius: 999px;
  min-width: 4px;
  transition: width 0.35s ease;
}
.bar.recharge { background: linear-gradient(90deg, #6366f1, #818cf8); }
.bar.consume { background: linear-gradient(90deg, #06b6d4, #22d3ee); }
.vals {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  gap: 2px;
}
.vals .r { color: #4f46e5; }
.vals .c { color: #0891b2; }
.legend {
  display: flex;
  gap: 16px;
  margin-top: 14px;
  font-size: 12px;
  color: var(--saas-text-2);
}
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}
.dot.r { background: #6366f1; }
.dot.c { background: #06b6d4; }
</style>
