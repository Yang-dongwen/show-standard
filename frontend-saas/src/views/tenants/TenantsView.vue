<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>租户门店</h1>
        <p class="page-desc">启停、续期、配额与店长代运维</p>
      </div>
      <div class="page-header-actions">
        <el-input
          v-model="keyword"
          clearable
          placeholder="搜索门店 / 码 / 店长"
          style="width: 220px"
          :prefix-icon="Search"
        />
        <el-select v-model="statusFilter" clearable placeholder="状态" style="width: 110px">
          <el-option label="正常" value="active" />
          <el-option label="停用" value="disabled" />
        </el-select>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="saas-table-wrap" v-loading="loading">
      <el-table :data="filtered" stripe empty-text="暂无租户">
        <el-table-column label="门店" min-width="180">
          <template #default="{ row }">
            <div class="shop-cell">
              <div class="avatar">{{ (row.shop_name || '?').slice(0, 1) }}</div>
              <div>
                <div class="name">{{ row.shop_name }}</div>
                <div class="key">{{ row.tenant_key }}</div>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="manager_username" label="店长" width="110" />
        <el-table-column label="套餐" width="100">
          <template #default="{ row }">
            <span class="plan-pill" :class="row.plan_code">{{ planLabel(row.plan_code) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="写模式" width="90">
          <template #default="{ row }">
            <el-tag :type="row.write_mode === 'readonly' ? 'warning' : 'info'" size="small" effect="light" round>
              {{ row.write_mode === 'readonly' ? '只读' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="expire_at" label="到期" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span :class="{ danger: isExpired(row.expire_at) }">{{ row.expire_at || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" width="100" show-overflow-tooltip />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small" effect="light" round>
              {{ row.status === 'active' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="showDetail(row)">管理</el-button>
            <el-button
              v-if="row.status === 'active'"
              link
              type="danger"
              @click="onSuspend(row)"
            >停用</el-button>
            <el-button v-else link type="success" @click="onActivate(row)">启用</el-button>
          </template>
        </el-table-column>
      </el-table>
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
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import TenantDrawer from '@/components/TenantDrawer.vue'
import { planLabel } from '@/utils/format.js'
import {
  activateTenant,
  fetchPlans,
  fetchTenantDetail,
  fetchTenants,
  suspendTenant
} from '@/api/http.js'

const loading = ref(false)
const tenants = ref([])
const plans = ref([])
const keyword = ref('')
const statusFilter = ref('')
const drawerOpen = ref(false)
const detail = ref(null)

const filtered = computed(() => {
  let list = tenants.value || []
  if (statusFilter.value) {
    list = list.filter((t) => t.status === statusFilter.value)
  }
  const q = keyword.value.trim().toLowerCase()
  if (!q) return list
  return list.filter((t) => {
    const blob = [t.shop_name, t.tenant_key, t.manager_username, t.tags]
      .map((x) => String(x || '').toLowerCase())
      .join(' ')
    return blob.includes(q)
  })
})

function isExpired(expireAt) {
  if (!expireAt) return false
  const t = Date.parse(String(expireAt).replace(' ', 'T'))
  return !Number.isNaN(t) && t < Date.now()
}

async function load() {
  loading.value = true
  try {
    tenants.value = (await fetchTenants()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function showDetail(row) {
  try {
    if (!plans.value.length) {
      plans.value = (await fetchPlans()) || []
    }
    detail.value = await fetchTenantDetail(row.id)
    drawerOpen.value = true
  } catch (e) {
    ElMessage.error(e.message)
  }
}

function onDetailUpdated(data) {
  detail.value = data
  load()
}

async function onSuspend(row) {
  await ElMessageBox.confirm(`停用「${row.shop_name}」后店长无法登录 C 端`, '确认停用', { type: 'warning' })
  await suspendTenant(row.id)
  ElMessage.success('已停用')
  load()
}

async function onActivate(row) {
  await activateTenant(row.id)
  ElMessage.success('已启用')
  load()
}

onMounted(() => {
  load()
  fetchPlans().then((p) => { plans.value = p || [] }).catch(() => {})
})
</script>

<style scoped>
.shop-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.avatar {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #e0e7ff, #c7d2fe);
  color: #4338ca;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.name { font-weight: 600; color: var(--saas-text); }
.key {
  font-size: 11px;
  color: var(--saas-text-3);
  font-variant-numeric: tabular-nums;
}
.plan-pill {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
  background: #f1f5f9;
  color: #475569;
}
.plan-pill.free { background: #f1f5f9; color: #64748b; }
.plan-pill.plus { background: #eef2ff; color: #4f46e5; }
.plan-pill.pro { background: #f5f3ff; color: #7c3aed; }
.danger { color: #e11d48; font-weight: 600; }
</style>
