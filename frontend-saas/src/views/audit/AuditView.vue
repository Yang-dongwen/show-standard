<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>操作审计</h1>
        <p class="page-desc">平台侧启停、续期、公告等写操作留痕</p>
      </div>
      <div class="page-header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="timeline-wrap" v-loading="loading">
      <div v-if="!audits.length" class="empty">暂无审计记录</div>
      <div v-else class="timeline">
        <div v-for="row in audits" :key="row.id" class="item">
          <div class="rail">
            <div class="dot" :class="tone(row.action)" />
            <div class="line" />
          </div>
          <div class="card">
            <div class="top">
              <span class="action">{{ row.action }}</span>
              <span class="time">{{ row.created_at }}</span>
            </div>
            <div class="meta">
              <span class="op">{{ row.operator || '—' }}</span>
              <span v-if="row.target_type" class="target">
                {{ row.target_type }}
                <template v-if="row.target_id"> · {{ row.target_id }}</template>
              </span>
            </div>
            <div v-if="row.detail" class="detail">{{ row.detail }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { fetchAudit } from '@/api/http.js'

const loading = ref(false)
const audits = ref([])

function tone(action) {
  const a = String(action || '').toUpperCase()
  if (a.includes('SUSPEND') || a.includes('REVOKE') || a.includes('RESET')) return 'rose'
  if (a.includes('RENEW') || a.includes('ACTIVATE') || a.includes('APPLY')) return 'emerald'
  if (a.includes('CREATE') || a.includes('ANNOUNCE')) return 'indigo'
  return 'slate'
}

async function load() {
  loading.value = true
  try {
    audits.value = (await fetchAudit(100)) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.timeline-wrap {
  min-height: 200px;
}
.empty {
  padding: 64px;
  text-align: center;
  color: var(--saas-text-3);
  background: #fff;
  border-radius: var(--saas-radius-lg);
  border: 1px dashed var(--saas-border);
}
.timeline {
  display: flex;
  flex-direction: column;
}
.item {
  display: grid;
  grid-template-columns: 28px 1fr;
  gap: 12px;
}
.rail {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 18px;
}
.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #94a3b8;
  box-shadow: 0 0 0 4px rgba(148, 163, 184, 0.2);
  flex-shrink: 0;
}
.dot.indigo { background: #6366f1; box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.18); }
.dot.emerald { background: #10b981; box-shadow: 0 0 0 4px rgba(16, 185, 129, 0.18); }
.dot.rose { background: #f43f5e; box-shadow: 0 0 0 4px rgba(244, 63, 94, 0.15); }
.dot.slate { background: #64748b; box-shadow: 0 0 0 4px rgba(100, 116, 139, 0.15); }
.line {
  flex: 1;
  width: 2px;
  background: #e2e8f0;
  margin: 6px 0 0;
  min-height: 24px;
}
.item:last-child .line { display: none; }
.card {
  background: #fff;
  border: 1px solid var(--saas-border);
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 10px;
  box-shadow: var(--saas-shadow-xs);
}
.top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: baseline;
}
.action {
  font-weight: 700;
  font-size: 13px;
  color: var(--saas-text);
  font-family: ui-monospace, Consolas, monospace;
}
.time {
  font-size: 12px;
  color: var(--saas-text-3);
  flex-shrink: 0;
}
.meta {
  margin-top: 6px;
  font-size: 12px;
  color: var(--saas-text-2);
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.op { font-weight: 600; }
.target {
  color: var(--saas-text-3);
  font-variant-numeric: tabular-nums;
}
.detail {
  margin-top: 8px;
  font-size: 13px;
  color: var(--saas-text-2);
  line-height: 1.5;
  padding: 8px 10px;
  background: #f8fafc;
  border-radius: 8px;
}
</style>
