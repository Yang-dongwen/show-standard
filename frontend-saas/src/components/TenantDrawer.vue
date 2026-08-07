<template>
  <el-drawer
    :model-value="modelValue"
    size="480px"
    destroy-on-close
    class="tenant-drawer"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <template #header>
      <div class="drawer-head" v-if="detail">
        <div class="shop-avatar">{{ (detail.shop_name || '店').slice(0, 1) }}</div>
        <div>
          <div class="shop-name">{{ detail.shop_name }}</div>
          <div class="shop-key">门店码 {{ detail.tenant_key }}</div>
        </div>
      </div>
      <span v-else>租户管理</span>
    </template>

    <template v-if="detail">
      <div class="status-row">
        <el-tag :type="detail.status === 'active' ? 'success' : 'danger'" effect="light" round size="small">
          {{ detail.status === 'active' ? '正常营业' : '已停用' }}
        </el-tag>
        <el-tag :type="detail.write_mode === 'readonly' ? 'warning' : 'info'" effect="plain" round size="small">
          {{ detail.write_mode === 'readonly' ? '只读模式' : '可写' }}
        </el-tag>
        <el-tag effect="plain" round size="small">{{ planLabel(detail.plan_code) }}</el-tag>
      </div>

      <div class="stat-grid">
        <div class="stat"><div class="n">{{ detail.cendStats?.activeCustomers ?? 0 }}</div><div class="l">活跃会员</div></div>
        <div class="stat"><div class="n">{{ detail.cendStats?.activeEmployees ?? 0 }}</div><div class="l">在岗员工</div></div>
        <div class="stat"><div class="n">{{ money(detail.cendStats?.totalBalance) }}</div><div class="l">余额合计</div></div>
        <div class="stat"><div class="n">{{ money(detail.cendStats?.totalConsume) }}</div><div class="l">累计消费</div></div>
      </div>

      <el-descriptions :column="1" border size="small" class="block">
        <el-descriptions-item label="店长">
          {{ detail.manager_username }} · {{ detail.manager_nickname || '—' }}
        </el-descriptions-item>
        <el-descriptions-item label="配额">
          会员 {{ detail.max_customers }} / 员工 {{ detail.max_employees }}
        </el-descriptions-item>
        <el-descriptions-item label="到期">
          {{ detail.expire_at || '未设置' }}
        </el-descriptions-item>
        <el-descriptions-item label="累计充值">
          {{ money(detail.cendStats?.totalRecharge) }}
        </el-descriptions-item>
        <el-descriptions-item label="小程序">
          使用门店码 + 店长账号登录云版
        </el-descriptions-item>
      </el-descriptions>

      <section class="section">
        <div class="section-title">
          <span>人工续期</span>
          <el-button size="small" @click="toggleReadonly">
            {{ detail.write_mode === 'readonly' ? '恢复可写' : '设为只读' }}
          </el-button>
        </div>
        <el-form label-position="top" class="compact-form">
          <div class="form-row">
            <el-form-item label="天数">
              <el-input-number v-model="renewForm.days" :min="1" :max="3650" controls-position="right" />
            </el-form-item>
            <el-form-item label="套餐码">
              <el-input v-model="renewForm.planCode" placeholder="free/plus/pro" />
            </el-form-item>
          </div>
          <div class="form-row">
            <el-form-item label="金额">
              <el-input-number v-model="renewForm.amount" :min="0" :precision="2" controls-position="right" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="renewForm.note" placeholder="可选" />
            </el-form-item>
          </div>
          <el-button type="primary" :loading="renewSaving" @click="doRenew">确认续期</el-button>
        </el-form>
      </section>

      <section class="section">
        <div class="section-title">近 7 日流水</div>
        <TrendBars :data="detail.last7Days || []" />
      </section>

      <section class="section">
        <div class="section-title">标签 / 备注</div>
        <el-form label-position="top" class="compact-form">
          <el-form-item label="标签">
            <el-input v-model="metaForm.tags" placeholder="如 VIP,风险" />
          </el-form-item>
          <el-form-item label="备注">
            <el-input v-model="metaForm.remark" type="textarea" :rows="2" />
          </el-form-item>
          <el-form-item label="延长到期（天，0=清空到期）">
            <el-input-number v-model="metaForm.expireDays" :min="0" controls-position="right" />
          </el-form-item>
          <el-button type="primary" plain :loading="metaSaving" @click="saveMeta">保存档案</el-button>
        </el-form>
      </section>

      <section class="section">
        <div class="section-title">套用套餐目录</div>
        <div class="plan-chips">
          <button
            v-for="p in plans"
            :key="p.code"
            type="button"
            class="plan-chip"
            :class="{ active: detail.plan_code === p.code }"
            @click="applyPlan(p.code)"
          >
            <span class="pc-name">{{ p.name }}</span>
            <span class="pc-code">{{ p.code }}</span>
          </button>
        </div>
      </section>

      <section class="section">
        <div class="section-title">自定义配额</div>
        <el-form label-position="top" class="compact-form">
          <div class="form-row">
            <el-form-item label="套餐码">
              <el-input v-model="planForm.planCode" />
            </el-form-item>
            <el-form-item label="会员上限">
              <el-input-number v-model="planForm.maxCustomers" :min="1" controls-position="right" />
            </el-form-item>
          </div>
          <el-form-item label="员工上限">
            <el-input-number v-model="planForm.maxEmployees" :min="1" controls-position="right" />
          </el-form-item>
          <el-button type="primary" plain :loading="planSaving" @click="savePlan">保存配额</el-button>
        </el-form>
      </section>

      <section class="section danger-zone">
        <div class="section-title">重置店长密码</div>
        <el-form label-position="top" class="compact-form">
          <el-form-item label="新密码">
            <el-input v-model="pwdForm" type="password" show-password placeholder="至少 6 位" />
          </el-form-item>
          <el-button type="danger" plain :loading="pwdSaving" @click="doResetPwd">重置密码</el-button>
        </el-form>
      </section>
    </template>
  </el-drawer>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TrendBars from '@/components/TrendBars.vue'
import { money, planLabel } from '@/utils/format.js'
import {
  applyTenantPlan,
  renewTenant,
  resetManagerPassword,
  setWriteMode,
  updateTenantMeta,
  updateTenantPlan
} from '@/api/http.js'

const props = defineProps({
  modelValue: Boolean,
  detail: { type: Object, default: null },
  plans: { type: Array, default: () => [] }
})

const emit = defineEmits(['update:modelValue', 'updated'])

const renewSaving = ref(false)
const renewForm = reactive({ days: 30, planCode: 'plus', amount: 0, note: '' })
const metaSaving = ref(false)
const metaForm = reactive({ tags: '', remark: '', expireDays: 0 })
const planSaving = ref(false)
const planForm = reactive({ planCode: 'free', maxCustomers: 5000, maxEmployees: 50 })
const pwdForm = ref('')
const pwdSaving = ref(false)

watch(
  () => props.detail,
  (d) => {
    if (!d) return
    planForm.planCode = d.plan_code || 'free'
    planForm.maxCustomers = Number(d.max_customers) || 5000
    planForm.maxEmployees = Number(d.max_employees) || 50
    metaForm.tags = d.tags || ''
    metaForm.remark = d.remark || ''
    metaForm.expireDays = 0
    renewForm.planCode = d.plan_code || 'plus'
    renewForm.days = 30
    renewForm.amount = 0
    renewForm.note = ''
    pwdForm.value = ''
  },
  { immediate: true }
)

function pushUpdated(data) {
  emit('updated', data)
}

async function doRenew() {
  if (!props.detail?.id) return
  renewSaving.value = true
  try {
    const data = await renewTenant(props.detail.id, { ...renewForm })
    ElMessage.success('续期成功')
    pushUpdated(data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    renewSaving.value = false
  }
}

async function toggleReadonly() {
  if (!props.detail?.id) return
  const next = props.detail.write_mode === 'readonly' ? 'normal' : 'readonly'
  try {
    const data = await setWriteMode(props.detail.id, next)
    ElMessage.success(next === 'readonly' ? '已设为只读' : '已恢复可写')
    pushUpdated(data)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function saveMeta() {
  if (!props.detail?.id) return
  metaSaving.value = true
  try {
    const data = await updateTenantMeta(props.detail.id, {
      tags: metaForm.tags,
      remark: metaForm.remark,
      expireDays: metaForm.expireDays
    })
    ElMessage.success('档案已保存')
    pushUpdated(data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    metaSaving.value = false
  }
}

async function applyPlan(code) {
  if (!props.detail?.id) return
  try {
    const data = await applyTenantPlan(props.detail.id, code)
    ElMessage.success('已套用 ' + code)
    pushUpdated(data)
  } catch (e) {
    ElMessage.error(e.message)
  }
}

async function savePlan() {
  if (!props.detail?.id) return
  planSaving.value = true
  try {
    const data = await updateTenantPlan(props.detail.id, { ...planForm })
    ElMessage.success('配额已更新')
    pushUpdated(data)
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    planSaving.value = false
  }
}

async function doResetPwd() {
  if (!props.detail?.id) return
  if (!pwdForm.value || pwdForm.value.length < 6) {
    ElMessage.warning('密码至少 6 位')
    return
  }
  await ElMessageBox.confirm('确认重置该门店店长密码？', '危险操作', { type: 'warning' })
  pwdSaving.value = true
  try {
    await resetManagerPassword(props.detail.id, pwdForm.value)
    ElMessage.success('密码已重置')
    pwdForm.value = ''
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    pwdSaving.value = false
  }
}
</script>

<style scoped>
.drawer-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.shop-avatar {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.shop-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--saas-text);
}
.shop-key {
  font-size: 12px;
  color: var(--saas-text-3);
  margin-top: 2px;
  font-variant-numeric: tabular-nums;
}
.status-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}
.stat-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
  margin-bottom: 16px;
}
.stat {
  background: #f8fafc;
  border: 1px solid var(--saas-border-soft);
  border-radius: 12px;
  padding: 12px;
}
.stat .n {
  font-size: 18px;
  font-weight: 700;
  color: var(--saas-text);
  font-variant-numeric: tabular-nums;
}
.stat .l {
  font-size: 11px;
  color: var(--saas-text-3);
  margin-top: 2px;
}
.block { margin-bottom: 8px; }
.section {
  margin-top: 22px;
  padding-top: 18px;
  border-top: 1px solid var(--saas-border-soft);
}
.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  font-weight: 600;
  color: var(--saas-text);
  margin-bottom: 12px;
}
.compact-form :deep(.el-form-item) {
  margin-bottom: 12px;
}
.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}
.plan-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.plan-chip {
  border: 1px solid var(--saas-border);
  background: #fff;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
  min-width: 96px;
  transition: border-color 0.15s, box-shadow 0.15s;
}
.plan-chip:hover {
  border-color: #a5b4fc;
}
.plan-chip.active {
  border-color: #6366f1;
  background: #eef2ff;
  box-shadow: 0 0 0 3px rgba(99, 102, 241, 0.12);
}
.pc-name {
  display: block;
  font-weight: 600;
  font-size: 13px;
  color: var(--saas-text);
}
.pc-code {
  display: block;
  font-size: 11px;
  color: var(--saas-text-3);
  margin-top: 2px;
}
.danger-zone {
  background: #fff1f2;
  border: 1px solid #fecdd3;
  border-radius: 12px;
  padding: 14px;
  margin-top: 22px;
  border-top: 1px solid #fecdd3;
}
.danger-zone .section-title {
  color: #be123c;
}
</style>
