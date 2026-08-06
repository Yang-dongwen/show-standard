<template>
  <div class="page-view">
    <div class="page-toolbar">
      <div class="page-toolbar-left">
        <el-input
          v-model="listKeyword"
          class="field-md"
          clearable
          placeholder="员工姓名"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button @click="reload">查询</el-button>
      </div>
      <div class="page-toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增员工</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card" v-loading="loading">
      <el-table :data="employees" stripe height="100%">
        <template #empty>
          <EmptyHint description="暂无员工" action-text="新增员工" @action="openCreate" />
        </template>
        <el-table-column prop="name" label="姓名" min-width="160" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '在岗' : '停用' }}
            </el-tag>
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
      :title="form.id ? '编辑员工' : '新增员工'"
      size="400px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="left" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" class="field-sm" maxlength="20" placeholder="姓名" />
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
import { Search, Plus } from '@element-plus/icons-vue'
import {
  createEmployee,
  fetchEmployees,
  toggleEmployeeStatus,
  updateEmployee
} from '@/api/employee.js'
import { debounce } from '@/utils/debounce.js'
import EmptyHint from '@/components/common/EmptyHint.vue'

const loading = ref(false)
const saving = ref(false)
const drawerVisible = ref(false)
const listKeyword = ref('')
const employees = ref([])
const formRef = ref()
const pager = reactive({ page: 1, size: 10, total: 0 })
const form = reactive({ id: '', name: '' })
const rules = {
  name: [{ required: true, message: '请输入员工姓名', trigger: 'blur' }]
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
    const data = await fetchEmployees({
      keyword: listKeyword.value.trim(),
      page: pager.page,
      size: pager.size
    })
    employees.value = data.items || []
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

function resetForm() {
  form.id = ''
  form.name = ''
}

function openCreate() {
  resetForm()
  drawerVisible.value = true
}

function openEdit(e) {
  form.id = e.id
  form.name = e.name
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
    const payload = { name: form.name }
    if (form.id) {
      await updateEmployee(form.id, payload)
      ElMessage.success('员工更新成功')
    } else {
      await createEmployee(payload)
      ElMessage.success('员工创建成功')
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
      `确定${row.status === 'active' ? '停用' : '恢复'}员工「${row.name}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await toggleEmployeeStatus(row.id)
    ElMessage.success('员工状态已更新')
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
  debouncedReload.cancel()
  if (unregister) unregister()
})
</script>
