<template>
  <div v-loading="loading" class="page-view">
    <div class="stat-grid">
      <StatCard label="活跃会员" :value="summary.activeCustomers || 0" :icon="User" tone="indigo" />
      <StatCard label="账户总余额" :value="money(summary.totalBalance)" :icon="Wallet" tone="green" />
      <StatCard label="今日充值" :value="money(summary.todayRecharge)" :icon="TrendCharts" tone="blue" />
      <StatCard label="今日消费" :value="money(summary.todayConsume)" :icon="ShoppingCart" tone="amber" />
    </div>

    <div class="overview-grid">
      <el-card shadow="never" class="panel">
        <template #header>
          <div class="card-head">
            <span>今日目标</span>
            <span class="target-pct">{{ targetProgress }}%</span>
          </div>
        </template>
        <div class="target-nums">
          {{ money(todayFlow) }} <em>/ {{ money(dailyTarget) }}</em>
        </div>
        <el-progress :percentage="targetProgress" :stroke-width="12" :show-text="false" color="#5b5ce2" />
        <p class="muted">
          {{ targetGap <= 0 ? '今日目标已达成，继续冲刺！' : `还差 ${money(targetGap)} 达成今日目标` }}
        </p>
        <div class="target-actions">
          <el-button size="small" @click="adjustTarget(-500)">目标 -500</el-button>
          <el-button size="small" type="primary" plain @click="adjustTarget(500)">目标 +500</el-button>
        </div>
      </el-card>

      <el-card shadow="never" class="panel">
        <template #header>
          <span class="section-title">快捷入口</span>
        </template>
        <div class="quick-grid">
          <el-button type="primary" :icon="UserFilled" @click="$router.push('/app/customers')">新增会员</el-button>
          <el-button type="success" plain :icon="Wallet" @click="$router.push('/app/transactions')">去充值/消费</el-button>
          <el-button plain :icon="TrendCharts" @click="$router.push('/app/reports')">查看报表</el-button>
          <el-button plain :icon="Avatar" @click="$router.push('/app/employees')">员工管理</el-button>
        </div>
        <p class="muted tip-line">把每一次服务做到位，财富会按时来敲门。</p>
      </el-card>

      <el-card shadow="never" class="panel tips-panel">
        <template #header>
          <span class="section-title">今日建议</span>
        </template>
        <ul class="tips-list">
          <li v-for="tip in operationTips" :key="tip">
            <span class="dot" />
            <span>{{ tip }}</span>
          </li>
        </ul>
      </el-card>

      <el-card shadow="never" class="panel lucky-panel">
        <template #header>
          <div class="card-head">
            <span class="section-title">趣味 · 发财签</span>
            <el-tag size="small" effect="plain" type="warning">可选</el-tag>
          </div>
        </template>
        <p class="lucky-text">{{ luckyMessage }}</p>
        <el-button type="primary" plain :loading="luckyAnimating" @click="drawLucky">
          {{ luckyAnimating ? '开运中...' : '抽一签' }}
        </el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref } from 'vue'
import { User, Wallet, TrendCharts, ShoppingCart, UserFilled, Avatar } from '@element-plus/icons-vue'
import StatCard from '@/components/common/StatCard.vue'
import { fetchDashboardSummary } from '@/api/report.js'
import { money } from '@/utils/format.js'

const TARGET_KEY = 'show.dailyTarget'

const loading = ref(false)
const summary = reactive({})
const dailyTarget = ref(Number(localStorage.getItem(TARGET_KEY)) || 3000)
const luckyMessage = ref('今日宜稳扎稳打，客单自然上涨。')
const luckyAnimating = ref(false)

const luckyNotes = [
  '上上签：今日适合主推高复购服务，财气在线。',
  '中上签：老客回流运不错，主动问候会有惊喜。',
  '平稳签：节奏稳住，细节做好，现金流会更健康。',
  '进取签：适合做会员转化，今天开卡成功率偏高。',
  '丰收签：团队配合顺，连单概率提升。'
]

const todayFlow = computed(
  () => Number(summary.todayRecharge || 0) + Number(summary.todayConsume || 0)
)
const targetProgress = computed(() => {
  const target = Number(dailyTarget.value || 0)
  if (target <= 0) return 0
  return Math.min(100, Math.round((todayFlow.value / target) * 100))
})
const targetGap = computed(() => Math.max(0, Number(dailyTarget.value || 0) - todayFlow.value))
const operationTips = computed(() => {
  const activeCustomers = Number(summary.activeCustomers || 0)
  const todayRecharge = Number(summary.todayRecharge || 0)
  const todayConsume = Number(summary.todayConsume || 0)
  const balance = Number(summary.totalBalance || 0)
  return [
    `活跃会员 ${activeCustomers} 位，建议优先回访高频到店客户。`,
    `今日充值 ${money(todayRecharge)}，可主推次卡提升复购。`,
    `今日消费 ${money(todayConsume)}，收银后可顺带做会员转化。`,
    `当前总余额 ${money(balance)}，关注沉睡会员唤醒机会。`
  ]
})

function adjustTarget(delta) {
  dailyTarget.value = Math.max(500, Number(dailyTarget.value || 0) + delta)
  localStorage.setItem(TARGET_KEY, String(dailyTarget.value))
}

async function load() {
  loading.value = true
  try {
    Object.assign(summary, await fetchDashboardSummary())
  } finally {
    loading.value = false
  }
}

function drawLucky() {
  if (luckyAnimating.value) return
  luckyAnimating.value = true
  setTimeout(() => {
    luckyMessage.value = luckyNotes[Math.floor(Math.random() * luckyNotes.length)]
    luckyAnimating.value = false
  }, 380)
}

const registerRefresh = inject('registerRefresh', null)
let unregister = null
onMounted(async () => {
  await load()
  if (registerRefresh) unregister = registerRefresh(load)
})
onUnmounted(() => {
  if (unregister) unregister()
})
</script>

<style scoped>
.overview-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
}

.panel {
  min-height: 200px;
}

.target-pct {
  font-size: 18px;
  font-weight: 800;
  color: var(--accent);
}

.target-nums {
  font-size: 16px;
  font-weight: 700;
  margin-bottom: 12px;
  font-variant-numeric: tabular-nums;
}

.target-nums em {
  font-style: normal;
  color: var(--text-secondary);
  font-weight: 500;
  font-size: 13px;
}

.muted {
  margin: 12px 0;
  color: var(--text-secondary);
  font-size: 13px;
}

.target-actions,
.quick-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.quick-grid .el-button {
  flex: 1 1 140px;
}

.tip-line {
  margin-top: 16px;
  margin-bottom: 0;
}

.tips-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.tips-list li {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  font-size: 13px;
  line-height: 1.55;
  color: var(--text-secondary);
}

.tips-list .dot {
  width: 8px;
  height: 8px;
  margin-top: 6px;
  border-radius: 50%;
  background: linear-gradient(135deg, #6d6df0, #5b5ce2);
  flex-shrink: 0;
}

.lucky-text {
  min-height: 56px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
  margin-bottom: 14px;
}

.lucky-panel {
  background: linear-gradient(160deg, #fffaf0, #fff);
}

@media (max-width: 980px) {
  .overview-grid {
    grid-template-columns: 1fr;
  }
}
</style>
