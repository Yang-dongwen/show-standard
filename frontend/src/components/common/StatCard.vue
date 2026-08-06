<template>
  <div class="stat-card" :class="tone">
    <div class="stat-top">
      <div class="stat-icon">
        <el-icon :size="20"><component :is="icon" /></el-icon>
      </div>
      <div class="stat-label">{{ label }}</div>
    </div>
    <div class="stat-value">{{ value }}</div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  icon: { type: [Object, String], required: true },
  tone: {
    type: String,
    default: 'indigo',
    validator: (v) => ['indigo', 'green', 'blue', 'amber'].includes(v)
  }
})
</script>

<style scoped>
.stat-card {
  border-radius: 14px;
  padding: 16px 18px 14px;
  background: #fff;
  box-shadow: var(--shadow-card);
  border: 1px solid rgba(15, 23, 42, 0.04);
  min-height: 104px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.stat-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--shadow-md);
}

.stat-card::after {
  content: '';
  position: absolute;
  right: -20px;
  top: -20px;
  width: 96px;
  height: 96px;
  border-radius: 50%;
  opacity: 0.12;
  pointer-events: none;
}

.stat-top {
  display: flex;
  align-items: center;
  gap: 10px;
}

.stat-icon {
  width: 38px;
  height: 38px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}

.stat-label {
  font-size: 13px;
  color: var(--text-secondary);
  font-weight: 500;
}

.stat-value {
  margin-top: 12px;
  font-size: 24px;
  font-weight: 800;
  letter-spacing: -0.02em;
  font-variant-numeric: tabular-nums;
  color: var(--text-primary);
  line-height: 1.15;
}

.stat-card.indigo .stat-icon {
  background: rgba(91, 92, 226, 0.12);
  color: #5b5ce2;
}
.stat-card.indigo::after {
  background: #5b5ce2;
}

.stat-card.green .stat-icon {
  background: rgba(16, 185, 129, 0.12);
  color: #059669;
}
.stat-card.green::after {
  background: #10b981;
}

.stat-card.blue .stat-icon {
  background: rgba(14, 165, 233, 0.12);
  color: #0284c7;
}
.stat-card.blue::after {
  background: #0ea5e9;
}

.stat-card.amber .stat-icon {
  background: rgba(245, 158, 11, 0.14);
  color: #d97706;
}
.stat-card.amber::after {
  background: #f59e0b;
}
</style>
