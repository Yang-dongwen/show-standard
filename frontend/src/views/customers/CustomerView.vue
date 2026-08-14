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
        <el-button :icon="Upload" @click="openImport">导入</el-button>
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
        <el-table-column v-if="canViewVerify" label="校验码" width="110">
          <template #default="{ row }">
            <el-button link type="primary" @click="toggleVerify(row.id)">
              {{ verifyVisible[row.id] ? row.verifyCode || '----' : '****' }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column v-else label="校验码" width="100">
          <template #default>
            <span class="muted">保密</span>
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
        <el-form-item v-if="canViewVerify || !form.id" label="校验码" prop="verifyCode">
          <el-input
            v-model="form.verifyCode"
            class="field-xs"
            maxlength="4"
            :placeholder="form.id && !canViewVerify ? '无权限查看' : '4位'"
            :disabled="!!form.id && !canViewVerify"
          />
          <div class="field-tip">校验码用于到店消费核对，不等于支付密码；非店长默认不可查看明文。</div>
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

    <el-dialog
      v-model="importVisible"
      title="导入会员"
      width="560px"
      destroy-on-close
      @closed="resetImport"
    >
      <div class="import-tips">
        <p>用于从其他平台迁移会员。请先下载模板，按列整理后上传 <strong>CSV</strong> 文件。</p>
        <p class="muted">
          必填：会员姓名、手机号。可选：校验码（默认手机后四位）、余额、备注、状态（正常/停用）。
          手机号重复的行会跳过，不影响其他行。单次最多 1000 行。
          Excel 编辑后可直接「另存为 CSV」；系统兼容 UTF-8 与中文 Windows 默认 GBK。
        </p>
      </div>
      <div class="import-actions">
        <el-button :icon="Download" :loading="templateLoading" @click="downloadTemplate">
          下载导入模板
        </el-button>
      </div>
      <el-upload
        ref="uploadRef"
        class="import-upload"
        drag
        :auto-upload="false"
        :limit="1"
        accept=".csv,text/csv"
        :on-change="onImportFileChange"
        :on-exceed="onImportExceed"
        :on-remove="onImportRemove"
      >
        <div class="el-upload__text">将 CSV 拖到此处，或 <em>点击选择</em></div>
      </el-upload>
      <div v-if="importResult" class="import-result">
        <el-alert
          :type="importResult.failed ? 'warning' : 'success'"
          :closable="false"
          show-icon
          :title="`导入完成：成功 ${importResult.success}，失败 ${importResult.failed}（共 ${importResult.total} 行）`"
        />
        <el-table
          v-if="importResult.errors && importResult.errors.length"
          class="import-error-table"
          :data="importResult.errors"
          size="small"
          max-height="220"
        >
          <el-table-column prop="row" label="行号" width="70" />
          <el-table-column prop="phone" label="手机号" min-width="120" />
          <el-table-column prop="message" label="原因" min-width="180" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="importVisible = false">关闭</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="submitImport">
          开始导入
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Plus, Download, Upload } from '@element-plus/icons-vue'
import {
  createCustomer,
  fetchCustomers,
  importCustomers,
  toggleCustomerStatus,
  updateCustomer
} from '@/api/customer.js'
import { downloadWithAuth } from '@/utils/download.js'
import { last4 } from '@/utils/format.js'
import { debounce } from '@/utils/debounce.js'
import { hasPermission } from '@/utils/permissions.js'
import MoneyText from '@/components/common/MoneyText.vue'
import EmptyHint from '@/components/common/EmptyHint.vue'

/** 仅店长默认可看明文校验码（与后端 customers:verify 对齐） */
const canViewVerify = computed(() => hasPermission('customers:verify'))

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const drawerVisible = ref(false)
const importVisible = ref(false)
const importing = ref(false)
const templateLoading = ref(false)
const importFile = ref(null)
const importResult = ref(null)
const uploadRef = ref()
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
  name: [
    { required: true, message: '请输入会员姓名', trigger: 'blur' },
    { max: 32, message: '会员姓名最多32个字', trigger: 'blur' }
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '手机号须为11位且以1开头', trigger: 'blur' }
  ],
  remark: [{ max: 200, message: '备注最多200个字', trigger: 'blur' }],
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

function openImport() {
  resetImport()
  importVisible.value = true
}

function resetImport() {
  importFile.value = null
  importResult.value = null
  importing.value = false
  templateLoading.value = false
  uploadRef.value?.clearFiles?.()
}

function onImportFileChange(file) {
  const raw = file?.raw
  if (!raw) {
    importFile.value = null
    return
  }
  const name = (raw.name || '').toLowerCase()
  if (!name.endsWith('.csv')) {
    ElMessage.warning('请上传 .csv 文件')
    uploadRef.value?.clearFiles?.()
    importFile.value = null
    return
  }
  importFile.value = raw
  importResult.value = null
}

function onImportExceed() {
  ElMessage.warning('一次只能选择一个文件，请先移除已选文件')
}

function onImportRemove() {
  importFile.value = null
}

async function downloadTemplate() {
  templateLoading.value = true
  try {
    await downloadWithAuth('/api/customers/import-template', 'customer-import-template.csv')
  } finally {
    templateLoading.value = false
  }
}

async function submitImport() {
  if (!importFile.value) {
    ElMessage.warning('请先选择 CSV 文件')
    return
  }
  importing.value = true
  try {
    const data = await importCustomers(importFile.value)
    importResult.value = data || { total: 0, success: 0, failed: 0, errors: [] }
    if (data?.success > 0) {
      ElMessage.success(`成功导入 ${data.success} 位会员`)
      await load()
    } else if (data?.failed > 0) {
      ElMessage.warning('导入未成功，请查看失败原因')
    }
  } catch {
    // handled by requestForm
  } finally {
    importing.value = false
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

<style scoped>
.field-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.4;
  margin-top: 4px;
}
.muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
.import-tips {
  margin-bottom: 12px;
  line-height: 1.5;
  font-size: 14px;
}
.import-tips p {
  margin: 0 0 6px;
}
.import-actions {
  margin-bottom: 12px;
}
.import-upload {
  width: 100%;
}
.import-result {
  margin-top: 16px;
}
.import-error-table {
  margin-top: 10px;
}
</style>
