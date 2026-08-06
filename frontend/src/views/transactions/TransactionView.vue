<template>
  <div class="page-view">
    <el-row :gutter="14">
      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="op-card">
          <template #header>
            <div class="card-head">
              <span>会员充值</span>
              <el-tag type="success" size="small" effect="light" round>入账</el-tag>
            </div>
          </template>
          <el-form
            ref="rechargeRef"
            :model="rechargeForm"
            :rules="rechargeRules"
            label-position="left"
            label-width="72px"
          >
            <el-form-item label="会员" prop="customerId">
              <el-select
                v-model="rechargeForm.customerId"
                class="field-lg"
                filterable
                clearable
                remote
                reserve-keyword
                :remote-method="searchCustomers"
                :loading="selectLoading"
                placeholder="姓名/手机号"
              >
                <el-option
                  v-for="c in customerOptions"
                  :key="c.id"
                  :label="`${c.name} / ${c.phone}`"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="金额" prop="amount">
              <el-input-number
                v-model="rechargeForm.amount"
                class="field-md"
                :min="0.01"
                :precision="2"
                :step="50"
                controls-position="right"
              />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="rechargeForm.remark" class="field-md" placeholder="选填" />
            </el-form-item>
            <el-form-item label=" ">
              <el-button type="primary" :loading="recharging" @click="submitRecharge">确认充值</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :md="12">
        <el-card shadow="never" class="op-card">
          <template #header>
            <div class="card-head">
              <span>会员消费</span>
              <el-tag type="warning" size="small" effect="light" round>扣款</el-tag>
            </div>
          </template>
          <el-form
            ref="consumeRef"
            :model="consumeForm"
            :rules="consumeRules"
            label-position="left"
            label-width="72px"
          >
            <el-form-item label="会员" prop="customerId">
              <el-select
                v-model="consumeForm.customerId"
                class="field-lg"
                filterable
                clearable
                remote
                reserve-keyword
                :remote-method="searchCustomers"
                :loading="selectLoading"
                placeholder="姓名/手机号"
                @change="onConsumeCustomerChange"
              >
                <el-option
                  v-for="c in customerOptions"
                  :key="c.id"
                  :label="formatCustomerOption(c)"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="员工" prop="employeeId">
              <el-select v-model="consumeForm.employeeId" class="field-sm" clearable placeholder="员工">
                <el-option v-for="e in activeEmployees" :key="e.id" :label="e.name" :value="e.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="服务" prop="serviceTypeId">
              <el-select
                v-model="consumeForm.serviceTypeId"
                class="field-md"
                clearable
                placeholder="服务"
                @change="fillServicePrice"
              >
                <el-option
                  v-for="s in activeServices"
                  :key="s.id"
                  :label="`${s.name} / ${money(s.price)}`"
                  :value="s.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="金额" prop="amount">
              <el-input-number
                v-model="consumeForm.amount"
                class="field-md"
                :min="0.01"
                :precision="2"
                :step="10"
                controls-position="right"
                placeholder="选服务后自动带出"
              />
              <div v-if="selectedServiceHint" class="field-hint">{{ selectedServiceHint }}</div>
            </el-form-item>
            <el-form-item label="校验码" prop="verifyCode">
              <el-input v-model="consumeForm.verifyCode" class="field-xs" maxlength="4" placeholder="4位" />
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="consumeForm.remark" class="field-lg" placeholder="选填" />
            </el-form-item>
            <el-form-item label=" ">
              <el-button type="primary" :loading="consuming" @click="submitConsume">确认扣款</el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>

    <el-card shadow="never" class="table-card" v-loading="txLoading">
      <template #header>
        <div class="card-head">
          <span>交易流水</span>
          <div class="page-toolbar-right">
            <el-date-picker
              v-model="exportRange"
              type="daterange"
              value-format="YYYY-MM-DD"
              range-separator="至"
              start-placeholder="开始"
              end-placeholder="结束"
              :clearable="false"
              class="field-xl"
            />
            <el-input
              v-model="listKeyword"
              class="field-md"
              clearable
              placeholder="搜索流水"
              :prefix-icon="Search"
              @keyup.enter="reload"
              @clear="reload"
            />
            <el-button :loading="exporting" @click="handleExport">导出 CSV</el-button>
          </div>
        </div>
      </template>
      <el-table :data="transactions" stripe height="100%" :row-key="(row) => row.id">
        <template #empty>
          <EmptyHint description="暂无交易流水" />
        </template>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="row.type === 'recharge' ? 'success' : 'warning'" size="small" effect="light" round>
              {{ row.type === 'recharge' ? '充值' : '消费' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="customerName" label="会员" min-width="120" />
        <el-table-column label="金额" width="120">
          <template #default="{ row }">
            <MoneyText :value="row.amount" :tone="row.type === 'recharge' ? 'pos' : 'neg'" />
          </template>
        </el-table-column>
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      </el-table>
      <div class="pager-bar">
        <el-pagination
          v-model:current-page="pager.page"
          v-model:page-size="pager.size"
          background
          layout="total, prev, pager, next"
          :total="pager.total"
          @current-change="loadTransactions"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { fetchCustomers } from '@/api/customer.js'
import { fetchEmployees } from '@/api/employee.js'
import { fetchServices } from '@/api/serviceType.js'
import { createConsume, createRecharge, fetchTransactions } from '@/api/transaction.js'
import { downloadWithAuth } from '@/utils/download.js'
import { dateOffset, dateTime, last4, money } from '@/utils/format.js'
import { debounce } from '@/utils/debounce.js'
import MoneyText from '@/components/common/MoneyText.vue'
import EmptyHint from '@/components/common/EmptyHint.vue'

const selectLoading = ref(false)
const txLoading = ref(false)
const recharging = ref(false)
const consuming = ref(false)
const exporting = ref(false)
const listKeyword = ref('')
const customerOptions = ref([])
const employees = ref([])
const services = ref([])
const transactions = ref([])
const pager = reactive({ page: 1, size: 10, total: 0 })
const exportRange = ref([dateOffset(-30), dateOffset(0)])

const rechargeRef = ref()
const consumeRef = ref()
const rechargeForm = reactive({ customerId: '', amount: null, remark: '' })
const consumeForm = reactive({
  customerId: '',
  employeeId: '',
  serviceTypeId: '',
  amount: null,
  verifyCode: '',
  remark: ''
})

const rechargeRules = {
  customerId: [{ required: true, message: '请选择会员', trigger: 'change' }],
  amount: [{ required: true, message: '请输入充值金额', trigger: 'change' }]
}
const consumeRules = {
  customerId: [{ required: true, message: '请选择会员', trigger: 'change' }],
  employeeId: [{ required: true, message: '请选择员工', trigger: 'change' }],
  serviceTypeId: [{ required: true, message: '请选择服务', trigger: 'change' }],
  amount: [{ required: true, message: '请输入消费金额', trigger: 'change' }],
  verifyCode: [
    { required: true, message: '请输入校验码', trigger: 'blur' },
    { pattern: /^\d{4}$/, message: '校验码须为4位数字', trigger: 'blur' }
  ]
}

const activeEmployees = computed(() => employees.value.filter((e) => e.status === 'active'))
const activeServices = computed(() => services.value.filter((s) => s.status === 'active'))
const selectedServiceHint = computed(() => {
  const s = activeServices.value.find((x) => x.id === consumeForm.serviceTypeId)
  if (!s) return ''
  return `默认价 ${money(s.price)}，可手动修改`
})

const registerRefresh = inject('registerRefresh', null)
let unregister = null

const debouncedTxSearch = debounce(() => {
  pager.page = 1
  loadTransactions()
}, 320)

watch(listKeyword, () => debouncedTxSearch())

function formatCustomerOption(c) {
  const bal = c.balance != null ? money(c.balance) : '--'
  return `${c.name} / ${c.phone} / 余额 ${bal}`
}

async function searchCustomers(query) {
  selectLoading.value = true
  try {
    const data = await fetchCustomers({ keyword: (query || '').trim(), page: 1, size: 30 })
    customerOptions.value = (data.items || []).filter((c) => c.status === 'active')
  } catch {
    customerOptions.value = []
  } finally {
    selectLoading.value = false
  }
}

async function loadEmployees() {
  const data = await fetchEmployees({ keyword: '', page: 1, size: 100 })
  employees.value = data.items || []
}

async function loadServices() {
  services.value = await fetchServices()
}

async function loadTransactions() {
  txLoading.value = true
  try {
    const data = await fetchTransactions({
      keyword: listKeyword.value.trim(),
      page: pager.page,
      size: pager.size
    })
    transactions.value = data.items || []
    pager.page = data.page || 1
    pager.size = data.size || pager.size
    pager.total = data.total || 0
  } finally {
    txLoading.value = false
  }
}

async function loadAll() {
  await Promise.all([searchCustomers(''), loadEmployees(), loadServices(), loadTransactions()])
}

function reload() {
  debouncedTxSearch.cancel()
  pager.page = 1
  loadTransactions()
}

function onConsumeCustomerChange(id) {
  const c = customerOptions.value.find((x) => x.id === id)
  consumeForm.verifyCode = c ? last4(c.phone) : ''
}

/**
 * 服务与金额完全联动：
 * - 选中服务 → 始终写入该服务默认价（切换服务会覆盖金额）
 * - 清空服务 → 清空金额
 * 用户仍可在带出后手动改金额。
 */
function fillServicePrice() {
  const id = consumeForm.serviceTypeId
  if (!id) {
    consumeForm.amount = null
    return
  }
  const service = services.value.find((s) => s.id === id)
  if (service) {
    consumeForm.amount = Number(service.price)
  }
}

async function submitRecharge() {
  try {
    await rechargeRef.value.validate()
  } catch {
    return
  }
  recharging.value = true
  try {
    await createRecharge({
      customerId: rechargeForm.customerId,
      amount: rechargeForm.amount,
      remark: rechargeForm.remark
    })
    rechargeForm.customerId = ''
    rechargeForm.amount = null
    rechargeForm.remark = ''
    rechargeRef.value?.resetFields?.()
    ElMessage.success('充值成功')
    await Promise.all([searchCustomers(''), loadTransactions()])
  } catch {
    // handled
  } finally {
    recharging.value = false
  }
}

async function submitConsume() {
  try {
    await consumeRef.value.validate()
  } catch {
    return
  }
  try {
    await ElMessageBox.confirm(`确认扣款 ${money(consumeForm.amount)} 吗？`, '消费确认', {
      type: 'warning'
    })
  } catch {
    return
  }
  consuming.value = true
  try {
    await createConsume({ ...consumeForm })
    consumeForm.customerId = ''
    consumeForm.employeeId = ''
    consumeForm.serviceTypeId = ''
    consumeForm.amount = null
    consumeForm.verifyCode = ''
    consumeForm.remark = ''
    consumeRef.value?.resetFields?.()
    ElMessage.success('消费登记成功')
    await Promise.all([searchCustomers(''), loadTransactions()])
  } catch {
    // handled
  } finally {
    consuming.value = false
  }
}

async function handleExport() {
  exporting.value = true
  const [startDate, endDate] = exportRange.value || [dateOffset(-30), dateOffset(0)]
  try {
    await downloadWithAuth(
      `/api/export/transactions?startDate=${startDate}&endDate=${endDate}`,
      'transactions.csv'
    )
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await loadAll()
  if (registerRefresh) unregister = registerRefresh(loadAll)
})
onUnmounted(() => {
  debouncedTxSearch.cancel()
  if (unregister) unregister()
})
</script>

<style scoped>
.op-card {
  margin-bottom: 14px;
  min-height: 100%;
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.4;
}
</style>
