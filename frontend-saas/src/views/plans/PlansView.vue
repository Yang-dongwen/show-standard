<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>套餐目录</h1>
        <p class="page-desc">预设配额模板 · 在租户详情中一键套用</p>
      </div>
      <div class="page-header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div v-loading="loading" class="plan-grid">
      <div
        v-for="p in plans"
        :key="p.code"
        class="plan-card"
        :class="p.code"
      >
        <div class="plan-badge">{{ p.code }}</div>
        <h3>{{ p.name }}</h3>
        <p class="desc">{{ p.description || '—' }}</p>
        <ul class="features">
          <li>
            <span class="k">会员上限</span>
            <span class="v">{{ p.max_customers }}</span>
          </li>
          <li>
            <span class="k">员工上限</span>
            <span class="v">{{ p.max_employees }}</span>
          </li>
          <li>
            <span class="k">试用天数</span>
            <span class="v">{{ p.trial_days || 0 }} 天</span>
          </li>
        </ul>
        <div class="foot">
          <el-tag size="small" effect="plain" round>
            {{ p.status === 'active' ? '已启用' : p.status }}
          </el-tag>
          <span class="sort">排序 {{ p.sort_order ?? 0 }}</span>
        </div>
      </div>
    </div>

    <p class="saas-tip">
      套餐目录为运营配置模板；真正改门店配额请在「租户门店 → 管理」中套用或自定义。
    </p>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { fetchPlans } from '@/api/http.js'

const loading = ref(false)
const plans = ref([])

async function load() {
  loading.value = true
  try {
    plans.value = (await fetchPlans()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.plan-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}
.plan-card {
  position: relative;
  background: #fff;
  border: 1px solid var(--saas-border);
  border-radius: 18px;
  padding: 24px 22px 20px;
  box-shadow: var(--saas-shadow-xs);
  overflow: hidden;
  transition: transform 0.15s, box-shadow 0.15s;
}
.plan-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--saas-shadow-md);
}
.plan-card::before {
  content: '';
  position: absolute;
  inset: 0 0 auto 0;
  height: 4px;
  background: #cbd5e1;
}
.plan-card.free::before { background: linear-gradient(90deg, #94a3b8, #cbd5e1); }
.plan-card.plus::before { background: linear-gradient(90deg, #6366f1, #818cf8); }
.plan-card.pro::before { background: linear-gradient(90deg, #7c3aed, #a78bfa); }

.plan-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #64748b;
  background: #f1f5f9;
  padding: 3px 8px;
  border-radius: 6px;
}
.plan-card.plus .plan-badge { background: #eef2ff; color: #4f46e5; }
.plan-card.pro .plan-badge { background: #f5f3ff; color: #7c3aed; }

h3 {
  margin: 12px 0 6px;
  font-size: 22px;
  font-weight: 750;
  letter-spacing: -0.02em;
}
.desc {
  font-size: 13px;
  color: var(--saas-text-2);
  min-height: 40px;
  line-height: 1.5;
}
.features {
  list-style: none;
  margin: 18px 0;
  padding: 0;
  border-top: 1px solid var(--saas-border-soft);
  border-bottom: 1px solid var(--saas-border-soft);
}
.features li {
  display: flex;
  justify-content: space-between;
  padding: 12px 0;
  border-bottom: 1px dashed var(--saas-border-soft);
  font-size: 13px;
}
.features li:last-child { border-bottom: none; }
.features .k { color: var(--saas-text-2); }
.features .v { font-weight: 700; color: var(--saas-text); font-variant-numeric: tabular-nums; }
.foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.sort {
  font-size: 12px;
  color: var(--saas-text-3);
}
@media (max-width: 960px) {
  .plan-grid { grid-template-columns: 1fr; }
}
</style>
