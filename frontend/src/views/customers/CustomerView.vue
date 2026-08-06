<template>
  <div class="page-view">
    <div class="page-toolbar">
      <div class="page-toolbar-left">
        <el-input
          v-model="listKeyword"
          class="field-lg"
          clearable
          placeholder="姓名/手机号"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">查询</el-button>
      </div>
      <div class="page-toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增会员</el-button>
        <el-button :icon="Download" :loading="exporting" @click="handleExport">导出 CSV</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card" v-loading="loading">
      <el-table :data="customers" stripe height="100%" :row-key="(row) => row.id">
        <template #empty>
          <EmptyHint description="暂无会员" action-text="新增第一位会员" @action="openCreate" />
        </template>
        <el-table-column prop="name" label="姓名" min-width="100" />
        <el-table-column prop="phone" label="手机号" min-width="130" />
        <el-table-column label="校验码" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggleVerify(row.id)">
              {{ verifyVisible[row.id] ? row.verifyCode : '****' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="余额" width="130">
          <template #default="{ row }">
            <MoneyText :value="row.balance" tone="pos" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="handleToggle(row)">
              {{ row.status === 'active' ? '停用' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager-bar">
        <el-pagination
          v-model:current-page="pager.page"
          v-model:page-size="pager.size"
          background
          layout="total, prev, pager, next"
          :total="pager.total"
          @current-change="load"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="form.id ? '编辑会员' : '新增会员'"
      size="420px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="left" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" class="field-sm" maxlength="20" placeholder="姓名" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input
            v-model="form.phone"
            class="field-md"
            maxlength="11"
            placeholder="11位手机号"
            @input="syncVerifyCodeByPhone"
          />
        </el-form-item>
        <el-form-item label="校验码" prop="verifyCode">
          <el-input v-model="form.verifyCode" class="field-xs" maxlength="4" placeholder="4位" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="初次充值">
          <el-input-number
            v-model="form.initialRechargeAmount"
            class="field-md"
            :min="0"
            :precision="2"
            :step="50"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" class="field-full" type="textarea" :rows="2" placeholder="选填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { inject, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download } from '@element-plus/icons-vue'
import {
  createCustomer,
  fetchCustomers,
  toggleCustomerStatus,
  updateCustomer
} from '@/api/customer.js'
import { downloadWithAuth } from '@/utils/download.js'
import { last4 } from '@/utils/format.js'
import { debounce } from '@/utils/debounce.js'
import MoneyText from '@/components/common/MoneyText.vue'
import EmptyHint from '@/components/common/EmptyHint.vue'

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const drawerVisible = ref(false)
const listKeyword = ref('')
const customers = ref([])
const verifyVisible = reactive({})
const autoVerifyCode = ref('')
const formRef = ref()
const pager = reactive({ page: 1, size: 10, total: 0 })
const form = reactive({
  id: '',
  name: '',
  phone: '',
  verifyCode: '',
  initialRechargeAmount: 0,
  remark: ''
})

const rules = {
  name: [{ required: true, message: '请输入会员姓名', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  verifyCode: [
    {
      validator: (_r, v, cb) => {
        if (!v) return cb()
        if (!/^\d{4}$/.test(v)) return cb(new Error('校验码必须是4位数字'))
        cb()
      },
      trigger: 'blur'
    }
  ]
}

const registerRefresh = inject('registerRefresh', null)
let unregister = null

const debouncedReload = debounce(() => {
  pager.page = 1
  load()
}, 320)

watch(listKeyword, () => debouncedReload())

async function load() {
  loading.value = true
  try {
    const data = await fetchCustomers({
      keyword: listKeyword.value.trim(),
      page: pager.page,
      size: pager.size
    })
    customers.value = data.items || []
    pager.page = data.page || 1
    pager.size = data.size || pager.size
    pager.total = data.total || 0
  } finally {
    loading.value = false
  }
}

function reload() {
  debouncedReload.cancel()
  pager.page = 1
  load()
}

function toggleVerify(id) {
  verifyVisible[id] = !verifyVisible[id]
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.phone = ''
  form.verifyCode = ''
  form.initialRechargeAmount = 0
  form.remark = ''
  autoVerifyCode.value = ''
}

function openCreate() {
  resetForm()
  drawerVisible.value = true
}

function openEdit(c) {
  form.id = c.id
  form.name = c.name
  form.phone = c.phone
  form.verifyCode = c.verifyCode || ''
  form.initialRechargeAmount = 0
  form.remark = c.remark || ''
  autoVerifyCode.value = ''
  drawerVisible.value = true
}

function syncVerifyCodeByPhone() {
  if (form.id) return
  const current = String(form.verifyCode || '').trim()
  if (!current || current === autoVerifyCode.value) {
    form.verifyCode = last4(form.phone)
    autoVerifyCode.value = form.verifyCode
  }
}

async function save() {
  const el = formRef.value
  if (!el) return
  try {
    await el.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (!form.id && !form.verifyCode) form.verifyCode = last4(form.phone)
    const payload = {
      name: form.name,
      phone: form.phone,
      remark: form.remark,
      verifyCode: form.verifyCode
    }
    if (form.id) {
      await updateCustomer(form.id, payload)
      ElMessage.success('会员更新成功')
    } else {
      payload.initialRechargeAmount = form.initialRechargeAmount || 0
      await createCustomer(payload)
      ElMessage.success('会员创建成功')
    }
    drawerVisible.value = false
    await load()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

async function handleToggle(row) {
  try {
    await ElMessageBox.confirm(
      `确定${row.status === 'active' ? '停用' : '恢复'}会员「${row.name}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await toggleCustomerStatus(row.id)
    ElMessage.success('会员状态已更新')
    await load()
  } catch {
    // cancelled
  }
}

async function handleExport() {
  exporting.value = true
  try {
    await downloadWithAuth('/api/export/customers', 'customers.csv')
  } finally {
    exporting.value = false
  }
}

onMounted(async () => {
  await load()
  if (registerRefresh) unregister = registerRefresh(load)
})
onUnmounted(() => {
  debouncedReload.cancel()
  if (unregister) unregister()
})
</script>
