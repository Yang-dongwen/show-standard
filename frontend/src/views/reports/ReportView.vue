<template>
  <div class="page-view">
    <el-card shadow="never" class="filter-card">
      <div class="page-toolbar">
        <div class="page-toolbar-left">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            :clearable="false"
          />
          <el-button type="primary" :loading="loading" @click="load">查询</el-button>
        </div>
        <div class="page-toolbar-right">
          <el-button :loading="exporting" @click="handleExport">导出员工业绩 CSV</el-button>
        </div>
      </div>
    </el-card>

    <div class="stat-grid">
      <StatCard label="充值总额" :value="money(summary.total_recharge)" :icon="Wallet" tone="green" />
      <StatCard label="消费总额" :value="money(summary.total_consume)" :icon="ShoppingCart" tone="amber" />
      <StatCard label="净收入" :value="money(netIncome)" :icon="TrendCharts" tone="indigo" />
      <StatCard label="活跃会员" :value="summary.active_customers || 0" :icon="User" tone="blue" />
    </div>

    <el-row :gutter="14">
      <el-col :xs="24" :md="10">
        <el-card shadow="never" class="panel-card" v-loading="loading">
          <template #header>
            <span class="section-title">服务分布</span>
          </template>
          <div v-if="serviceChartData.length" class="service-list">
            <div v-for="row in serviceChartData" :key="row.serviceTypeId" class="service-row">
              <div class="service-meta">
                <span class="service-name">{{ row.serviceName }}</span>
                <span class="service-sub">{{ row.total_count }} 单 · {{ money(row.total_amount) }}</span>
              </div>
              <el-progress :percentage="row.barWidth" :stroke-width="10" :show-text="false" color="#5b5ce2" />
            </div>
          </div>
          <EmptyHint v-else description="暂无服务消费数据" />
        </el-card>
      </el-col>
      <el-col :xs="24" :md="14">
        <el-card shadow="never" class="panel-card table-card" v-loading="loading">
          <template #header>
            <div class="card-head">
              <span class="section-title">员工业绩</span>
              <div class="mini-metrics">
                <el-tag size="small" effect="plain">总会员 {{ summary.total_customers || 0 }}</el-tag>
                <el-tag size="small" type="success" effect="plain">新增 {{ summary.new_customers || 0 }}</el-tag>
              </div>
            </div>
          </template>
          <el-table :data="pagedPerformance" stripe height="100%">
            <template #empty>
              <EmptyHint description="暂无业绩数据" />
            </template>
            <el-table-column prop="employeeName" label="员工" min-width="120" />
            <el-table-column prop="total_count" label="订单数" width="100" />
            <el-table-column label="总金额" width="120">
              <template #default="{ row }">
                <MoneyText :value="row.total_amount" />
              </template>
            </el-table-column>
            <el-table-column label="客单价" width="120">
              <template #default="{ row }">
                <MoneyText :value="row.avg_amount" />
              </template>
            </el-table-column>
          </el-table>
          <div class="pager-bar">
            <el-pagination
              v-model:current-page="perfPage"
              :page-size="perfSize"
              background
              layout="total, prev, pager, next"
              :total="performance.length"
            />
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref } from 'vue'
import { Wallet, ShoppingCart, TrendCharts, User } from '@element-plus/icons-vue'
import StatCard from '@/components/common/StatCard.vue'
import MoneyText from '@/components/common/MoneyText.vue'
import EmptyHint from '@/components/common/EmptyHint.vue'
import {
  fetchEmployeePerformance,
  fetchReportSummary,
  fetchServiceBreakdown
} from '@/api/report.js'
import { downloadWithAuth } from '@/utils/download.js'
import { dateOffset, money, slicePage } from '@/utils/format.js'

const loading = ref(false)
const exporting = ref(false)
const dateRange = ref([dateOffset(-30), dateOffset(0)])
const summary = reactive({})
const performance = ref([])
const serviceBreakdown = ref([])
const perfPage = ref(1)
const perfSize = 8

const netIncome = computed(
  () => Number(summary.total_recharge || 0) - Number(summary.total_consume || 0)
)
const serviceChartMax = computed(() => {
  if (!serviceBreakdown.value.length) return 0
  return Math.max(...serviceBreakdown.value.map((s) => Number(s.total_amount || 0)))
})
const serviceChartData = computed(() => {
  const max = serviceChartMax.value
  return serviceBreakdown.value.map((s) => {
    const amount = Number(s.total_amount || 0)
    const baseWidth = max > 0 ? Math.round((amount / max) * 100) : 0
    return { ...s, barWidth: amount > 0 ? Math.max(baseWidth, 8) : 0 }
  })
})
const pagedPerformance = computed(() => slicePage(performance.value, perfPage.value, perfSize))

const registerRefresh = inject('registerRefresh', null)
let unregister = null

async function load() {
  loading.value = true
  try {
    const [startDate, endDate] = dateRange.value || [dateOffset(-30), dateOffset(0)]
    const params = { startDate, endDate }
    Object.assign(summary, await fetchReportSummary(params))
    performance.value = await fetchEmployeePerformance(params)
    serviceBreakdown.value = await fetchServiceBreakdown(params)
    perfPage.value = 1
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  exporting.value = true
  const [startDate, endDate] = dateRange.value || [dateOffset(-30), dateOffset(0)]
  try {
    await downloadWithAuth(
      `/api/export/employee-performance?startDate=${startDate}&endDate=${endDate}`,
      'employee-performance.csv'
    )
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await load()
  if (registerRefresh) unregister = registerRefresh(load)
})
onUnmounted(() => {
  if (unregister) unregister()
})
</script>

<style scoped>
.filter-card,
.panel-card {
  margin-bottom: 4px;
}

.mini-metrics {
  display: flex;
  gap: 8px;
}

.service-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.service-meta {
  display: flex;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 13px;
}

.service-name {
  font-weight: 600;
}

.service-sub {
  color: var(--text-secondary);
}
</style>
