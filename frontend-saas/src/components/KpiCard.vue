<template>
  <div class="kpi" :class="tone">
    <div class="kpi-icon">
      <el-icon :size="18"><component :is="icon" /></el-icon>
    </div>
    <div class="kpi-body">
      <div class="kpi-label">{{ label }}</div>
      <div class="kpi-value">{{ value }}</div>
      <div v-if="hint" class="kpi-hint">{{ hint }}</div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  label: { type: String, required: true },
  value: { type: [String, Number], required: true },
  hint: { type: String, default: '' },
  icon: { type: [Object, Function], required: true },
  tone: { type: String, default: 'indigo' }
})
</script>

<style scoped>
.kpi {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  padding: 18px 16px;
  background: var(--saas-bg-elevated);
  border: 1px solid var(--saas-border);
  border-radius: var(--saas-radius-lg);
  box-shadow: var(--saas-shadow-xs);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
  min-height: 96px;
}
.kpi:hover {
  transform: translateY(-1px);
  box-shadow: var(--saas-shadow-sm);
}
.kpi-icon {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.kpi-label {
  font-size: 12px;
  color: var(--saas-text-2);
  font-weight: 500;
}
.kpi-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.03em;
  color: var(--saas-text);
  line-height: 1.2;
}
.kpi-hint {
  margin-top: 4px;
  font-size: 11px;
  color: var(--saas-text-3);
}

.kpi.indigo .kpi-icon { background: rgba(99, 102, 241, 0.12); color: #6366f1; }
.kpi.cyan .kpi-icon { background: rgba(6, 182, 212, 0.12); color: #0891b2; }
.kpi.emerald .kpi-icon { background: rgba(16, 185, 129, 0.12); color: #059669; }
.kpi.amber .kpi-icon { background: rgba(245, 158, 11, 0.14); color: #d97706; }
.kpi.rose .kpi-icon { background: rgba(244, 63, 94, 0.12); color: #e11d48; }
.kpi.violet .kpi-icon { background: rgba(139, 92, 246, 0.12); color: #7c3aed; }
.kpi.slate .kpi-icon { background: rgba(100, 116, 139, 0.12); color: #475569; }
.kpi.sky .kpi-icon { background: rgba(14, 165, 233, 0.12); color: #0284c7; }
</style>
