<template>
  <div class="page-view">
    <div class="page-toolbar">
      <div class="page-toolbar-left">
        <el-tag type="info" effect="plain" round>仅店长可管理登录账号</el-tag>
      </div>
      <div class="page-toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增账号</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card" v-loading="loading">
      <el-table :data="accounts" stripe height="100%">
        <template #empty>
          <EmptyHint description="暂无账号" action-text="新增账号" @action="openCreate" />
        </template>
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="100" />
        <el-table-column label="角色" width="120">
          <template #default="{ row }">
            <el-tag
              :type="row.role === 'owner' ? 'danger' : row.role === 'cashier' ? 'warning' : 'info'"
              size="small"
              effect="light"
              round
            >
              {{ row.roleLabel || roleLabel(row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" min-width="160" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="openResetPwd(row)">重置密码</el-button>
            <el-button
              v-if="row.role !== 'owner'"
              link
              type="danger"
              @click="handleToggle(row)"
            >
              {{ row.status === 'active' ? '停用' : '恢复' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card shadow="never" class="hint-card">
      <div class="hint-title">角色说明</div>
      <ul class="hint-list">
        <li><strong>店长</strong>：全部菜单，可创建账号、改门店设置、审计与备份</li>
        <li><strong>收银员</strong>：总览 / 会员 / 充值消费（含冲正）/ 报表</li>
        <li><strong>店员</strong>：总览 / 会员 / 消费登记（不可充值、不可看报表与设置）</li>
      </ul>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="form.id ? '编辑账号' : '新增账号'"
      size="420px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="left" label-width="88px">
        <el-form-item v-if="!form.id" label="用户名" prop="username">
          <el-input v-model="form.username" class="field-md" maxlength="32" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input
            v-model="form.password"
            class="field-md"
            type="password"
            show-password
            placeholder="至少6位"
          />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="form.nickname" class="field-md" maxlength="12" show-word-limit placeholder="显示名称" />
        </el-form-item>
        <el-form-item v-if="!form.id || form.role !== 'owner'" label="角色" prop="role">
          <el-select v-model="form.role" class="field-md" :disabled="form.role === 'owner' && !!form.id">
            <el-option v-if="form.role === 'owner' && form.id" label="店长" value="owner" />
            <el-option label="收银员" value="cashier" />
            <el-option label="店员" value="staff" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="drawerVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-drawer>

    <el-dialog v-model="pwdVisible" title="重置密码" width="400px" align-center destroy-on-close>
      <p class="dialog-tip">为「{{ pwdTarget?.nickname || pwdTarget?.username }}」设置新密码</p>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="88px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirm">
          <el-input v-model="pwdForm.confirm" type="password" show-password placeholder="再次输入" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitResetPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { inject, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createStaffAccount,
  fetchStaffAccounts,
  resetStaffPassword,
  toggleStaffStatus,
  updateStaffAccount
} from '@/api/staff.js'
import { roleLabel } from '@/utils/permissions.js'
import EmptyHint from '@/components/common/EmptyHint.vue'

const loading = ref(false)
const saving = ref(false)
const accounts = ref([])
const drawerVisible = ref(false)
const formRef = ref()
const form = reactive({
  id: '',
  username: '',
  password: '',
  nickname: '',
  role: 'cashier'
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '至少6位', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }]
}

const pwdVisible = ref(false)
const pwdLoading = ref(false)
const pwdTarget = ref(null)
const pwdFormRef = ref()
const pwdForm = reactive({ newPassword: '', confirm: '' })
const pwdRules = {
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '至少6位', trigger: 'blur' }
  ],
  confirm: [
    { required: true, message: '请再次输入', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        if (v !== pwdForm.newPassword) cb(new Error('两次输入不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const registerRefresh = inject('registerRefresh', null)
let unregister = null

async function load() {
  loading.value = true
  try {
    accounts.value = (await fetchStaffAccounts()) || []
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = ''
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.role = 'cashier'
}

function openCreate() {
  resetForm()
  drawerVisible.value = true
}

function openEdit(row) {
  form.id = row.id
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname
  form.role = row.role
  drawerVisible.value = true
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateStaffAccount(form.id, {
        nickname: form.nickname,
        role: form.role === 'owner' ? undefined : form.role
      })
      ElMessage.success('账号已更新')
    } else {
      await createStaffAccount({
        username: form.username.trim(),
        password: form.password,
        nickname: form.nickname.trim(),
        role: form.role
      })
      ElMessage.success('账号已创建')
    }
    drawerVisible.value = false
    await load()
  } catch {
    // handled
  } finally {
    saving.value = false
  }
}

function openResetPwd(row) {
  pwdTarget.value = row
  pwdForm.newPassword = ''
  pwdForm.confirm = ''
  pwdVisible.value = true
}

async function submitResetPwd() {
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  pwdLoading.value = true
  try {
    await resetStaffPassword(pwdTarget.value.id, pwdForm.newPassword)
    ElMessage.success('密码已重置')
    pwdVisible.value = false
  } catch {
    // handled
  } finally {
    pwdLoading.value = false
  }
}

async function handleToggle(row) {
  try {
    await ElMessageBox.confirm(
      `确定${row.status === 'active' ? '停用' : '恢复'}账号「${row.username}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await toggleStaffStatus(row.id)
    ElMessage.success('状态已更新')
    await load()
  } catch {
    // cancelled
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
.hint-card {
  margin-top: 14px;
}
.hint-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: var(--text-primary);
}
.hint-list {
  margin: 0;
  padding-left: 18px;
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 13px;
}
.dialog-tip {
  margin: 0 0 12px;
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
