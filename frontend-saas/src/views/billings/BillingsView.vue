<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>续期账单</h1>
        <p class="page-desc">人工续期流水记录（离线对账用）</p>
      </div>
      <div class="page-header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="summary-row">
      <div class="sum-card">
        <div class="l">记录数</div>
        <div class="n">{{ billings.length }}</div>
      </div>
      <div class="sum-card">
        <div class="l">累计天数</div>
        <div class="n">{{ totalDays }}</div>
      </div>
      <div class="sum-card accent">
        <div class="l">累计金额</div>
        <div class="n">¥ {{ money(totalAmount) }}</div>
      </div>
    </div>

    <div class="saas-table-wrap" v-loading="loading">
      <el-table :data="billings" stripe size="small" empty-text="暂无续期记录">
        <el-table-column prop="created_at" label="时间" width="170" />
        <el-table-column prop="shop_name" label="门店" min-width="140" />
        <el-table-column label="套餐" width="90">
          <template #default="{ row }">
            <span class="plan">{{ planLabel(row.plan_code) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="days" label="天数" width="80" />
        <el-table-column label="金额" width="100" align="right">
          <template #default="{ row }">
            <span class="amt">¥ {{ money(row.amount) }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="操作人" width="110" />
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
      </el-table>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import { money, planLabel } from '@/utils/format.js'
import { fetchBillings } from '@/api/http.js'

const loading = ref(false)
const billings = ref([])

const totalDays = computed(() =>
  billings.value.reduce((s, r) => s + (Number(r.days) || 0), 0)
)
const totalAmount = computed(() =>
  billings.value.reduce((s, r) => s + (Number(r.amount) || 0), 0)
)

async function load() {
  loading.value = true
  try {
    billings.value = (await fetchBillings(100)) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.summary-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-bottom: 16px;
}
.sum-card {
  background: #fff;
  border: 1px solid var(--saas-border);
  border-radius: var(--saas-radius-lg);
  padding: 16px 18px;
  box-shadow: var(--saas-shadow-xs);
}
.sum-card .l {
  font-size: 12px;
  color: var(--saas-text-2);
}
.sum-card .n {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.02em;
}
.sum-card.accent {
  background: linear-gradient(135deg, #eef2ff, #fff);
  border-color: #c7d2fe;
}
.sum-card.accent .n { color: #4f46e5; }
.plan {
  font-size: 12px;
  font-weight: 600;
  color: #4f46e5;
}
.amt {
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
@media (max-width: 720px) {
  .summary-row { grid-template-columns: 1fr; }
}
</style>
