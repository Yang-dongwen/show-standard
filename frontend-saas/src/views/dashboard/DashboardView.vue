<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>驾驶舱</h1>
        <p class="page-desc">全平台门店健康度、今日 GMV 与风险信号一览</p>
      </div>
      <div class="page-header-actions">
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新数据</el-button>
      </div>
    </div>

    <div v-loading="loading" class="kpi-grid">
      <KpiCard label="活跃门店" :value="dash.tenantActive ?? 0" :hint="`共 ${dash.tenantTotal ?? 0} 家`" :icon="OfficeBuilding" tone="indigo" />
      <KpiCard label="今日充值" :value="money(dash.todayRecharge)" hint="全网合计" :icon="Coin" tone="emerald" />
      <KpiCard label="今日消费" :value="money(dash.todayConsume)" hint="全网 GMV" :icon="ShoppingCart" tone="cyan" />
      <KpiCard label="今日新开店" :value="dash.todayNewTenants ?? 0" hint="邀请码开店" :icon="Plus" tone="violet" />
      <KpiCard label="全网活跃会员" :value="dash.totalCustomers ?? 0" :icon="User" tone="sky" />
      <KpiCard label="已到期" :value="dash.expiredCount ?? 0" hint="需续期" :icon="AlarmClock" tone="rose" />
      <KpiCard label="只读门店" :value="dash.readonlyCount ?? 0" hint="禁止写入" :icon="Lock" tone="amber" />
      <KpiCard label="配额告警" :value="(dash.quotaAlerts || []).length" hint="≥80% 占用" :icon="Warning" tone="slate" />
    </div>

    <div class="grid-2 mt">
      <div class="saas-card">
        <div class="saas-card-head">
          <div>
            <h3>近 7 日充值 / 消费</h3>
            <p class="sub">条形对比趋势</p>
          </div>
        </div>
        <div class="saas-card-body">
          <TrendBars :data="dash.last7Days || []" />
        </div>
      </div>

      <div class="saas-card">
        <div class="saas-card-head">
          <div>
            <h3>消费 Top 门店</h3>
            <p class="sub">近 30 日</p>
          </div>
        </div>
        <div class="saas-card-body tight">
          <div v-if="!(dash.topShopsByConsume || []).length" class="empty">暂无排行数据</div>
          <div v-else class="rank-list">
            <div
              v-for="(row, i) in dash.topShopsByConsume"
              :key="row.tenant_key || i"
              class="rank-item"
              @click="goTenant(row)"
            >
              <div class="rank-no" :class="'n' + (i + 1)">{{ i + 1 }}</div>
              <div class="rank-meta">
                <div class="rank-name">{{ row.shop_name }}</div>
                <div class="rank-key">{{ row.tenant_key }}</div>
              </div>
              <div class="rank-val">{{ money(row.total_consume) }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="grid-2 mt">
      <div class="saas-card">
        <div class="saas-card-head">
          <div>
            <h3>14 日内到期</h3>
            <p class="sub">优先续期，避免断服</p>
          </div>
          <el-button text type="primary" @click="$router.push('/home/tenants')">全部租户</el-button>
        </div>
        <div class="saas-card-body tight">
          <el-table :data="dash.expiringSoon || []" size="small" empty-text="暂无即将到期门店">
            <el-table-column prop="shop_name" label="门店" min-width="120" />
            <el-table-column prop="expire_at" label="到期" min-width="140" show-overflow-tooltip />
            <el-table-column label="" width="72" align="right">
              <template #default="{ row }">
                <el-button link type="primary" @click="openTenant(row.id)">续期</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>

      <div class="saas-card">
        <div class="saas-card-head">
          <div>
            <h3>服务热度</h3>
            <p class="sub">近 30 日全网</p>
          </div>
        </div>
        <div class="saas-card-body tight">
          <el-table :data="dash.topServices || []" size="small" empty-text="暂无服务数据">
            <el-table-column prop="service_name" label="服务" min-width="100" />
            <el-table-column prop="total_count" label="次数" width="72" />
            <el-table-column label="金额" width="100" align="right">
              <template #default="{ row }">{{ money(row.total_amount) }}</template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

    <div v-if="(dash.quotaAlerts || []).length" class="saas-card mt alert-card">
      <div class="saas-card-head">
        <div>
          <h3>配额告警</h3>
          <p class="sub">会员或员工占用 ≥ 80%</p>
        </div>
      </div>
      <div class="saas-card-body tight">
        <el-table :data="dash.quotaAlerts" size="small">
          <el-table-column prop="shop_name" label="门店" min-width="120" />
          <el-table-column label="会员" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.customerWarn" type="danger" size="small" effect="light">
                {{ row.used_customers }}/{{ row.max_customers }}
              </el-tag>
              <span v-else class="muted">{{ row.used_customers }}/{{ row.max_customers }}</span>
            </template>
          </el-table-column>
          <el-table-column label="员工" width="140">
            <template #default="{ row }">
              <el-tag v-if="row.employeeWarn" type="warning" size="small" effect="light">
                {{ row.used_employees }}/{{ row.max_employees }}
              </el-tag>
              <span v-else class="muted">{{ row.used_employees }}/{{ row.max_employees }}</span>
            </template>
          </el-table-column>
          <el-table-column label="" width="80" align="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="openTenant(row.id)">详情</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <TenantDrawer
      v-model="drawerOpen"
      :detail="detail"
      :plans="plans"
      @updated="onDetailUpdated"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  Refresh,
  OfficeBuilding,
  Coin,
  ShoppingCart,
  Plus,
  User,
  AlarmClock,
  Lock,
  Warning
} from '@element-plus/icons-vue'
import KpiCard from '@/components/KpiCard.vue'
import TrendBars from '@/components/TrendBars.vue'
import TenantDrawer from '@/components/TenantDrawer.vue'
import { money } from '@/utils/format.js'
import { fetchDashboard, fetchPlans, fetchTenantDetail } from '@/api/http.js'

const router = useRouter()
const loading = ref(false)
const dash = ref({})
const plans = ref([])
const drawerOpen = ref(false)
const detail = ref(null)

async function load() {
  loading.value = true
  try {
    dash.value = (await fetchDashboard()) || {}
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function openTenant(id) {
  try {
    if (!plans.value.length) {
      plans.value = (await fetchPlans()) || []
    }
    detail.value = await fetchTenantDetail(id)
    drawerOpen.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function goTenant(row) {
  if (row?.id) openTenant(row.id)
  else router.push('/home/tenants')
}

function onDetailUpdated(data) {
  detail.value = data
  load()
}

onMounted(() => {
  load()
  fetchPlans().then((p) => { plans.value = p || [] }).catch(() => {})
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
}
.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.mt { margin-top: 16px; }
.tight { padding-top: 8px; padding-bottom: 8px; }
.empty {
  padding: 40px 0;
  text-align: center;
  color: var(--saas-text-3);
  font-size: 13px;
}
.rank-list { display: flex; flex-direction: column; gap: 4px; }
.rank-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 8px;
  border-radius: 10px;
  cursor: pointer;
  transition: background 0.12s;
}
.rank-item:hover { background: #f8fafc; }
.rank-no {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  font-size: 12px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.rank-no.n1 { background: linear-gradient(135deg, #fbbf24, #f59e0b); color: #fff; }
.rank-no.n2 { background: linear-gradient(135deg, #94a3b8, #64748b); color: #fff; }
.rank-no.n3 { background: linear-gradient(135deg, #fdba74, #ea580c); color: #fff; }
.rank-meta { flex: 1; min-width: 0; }
.rank-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--saas-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.rank-key {
  font-size: 11px;
  color: var(--saas-text-3);
  font-variant-numeric: tabular-nums;
}
.rank-val {
  font-size: 13px;
  font-weight: 700;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
}
.muted { color: var(--saas-text-2); font-size: 13px; }
.alert-card {
  border-color: #fecdd3;
  background: linear-gradient(180deg, #fff1f2 0%, #fff 48%);
}
@media (max-width: 1100px) {
  .kpi-grid { grid-template-columns: repeat(2, 1fr); }
  .grid-2 { grid-template-columns: 1fr; }
}
</style>
