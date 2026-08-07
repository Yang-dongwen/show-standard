<template>
  <div class="page-view">
    <el-card shadow="never" class="shop-card" v-loading="shopLoading">
      <template #header>
        <div class="card-head">
          <span>门店资料</span>
          <el-tag size="small" type="info" effect="plain">{{ shop.planCode || 'free' }}</el-tag>
        </div>
      </template>
      <el-form label-width="88px" class="shop-form" @submit.prevent>
        <el-form-item label="门店名称">
          <el-input v-model="shopForm.shopName" maxlength="64" show-word-limit class="field-md" />
        </el-form-item>
        <el-form-item label="门店码">
          <el-input :model-value="shop.tenantKey || '-'" readonly class="field-md" />
        </el-form-item>
        <el-form-item label="配额">
          <span class="quota-text">会员 {{ shop.customerQuota || '-' }} · 员工 {{ shop.employeeQuota || '-' }}</span>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="shopSaving" @click="saveShop">保存门店资料</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <div class="page-toolbar">
      <div class="page-toolbar-left">
        <span class="hint-text">管理门店服务项目与默认价格，消费时可快速带出</span>
      </div>
      <div class="page-toolbar-right">
        <el-button type="primary" :icon="Plus" @click="openCreate">新增服务</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card" v-loading="loading">
      <el-table :data="pagedServices" stripe height="100%">
        <template #empty>
          <EmptyHint description="暂无服务项目" action-text="新增服务" @action="openCreate" />
        </template>
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column label="价格" width="140">
          <template #default="{ row }">
            <MoneyText :value="row.price" />
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small" effect="light" round>
              {{ row.status === 'active' ? '启用' : '停用' }}
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
          v-model:current-page="page"
          :page-size="size"
          background
          layout="total, prev, pager, next"
          :total="services.length"
        />
      </div>
    </el-card>

    <el-drawer
      v-model="drawerVisible"
      :title="form.id ? '编辑服务' : '新增服务'"
      size="400px"
      destroy-on-close
      @closed="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-position="left" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" class="field-md" maxlength="30" placeholder="如：剪发" />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number
            v-model="form.price"
            class="field-md"
            :min="0"
            :precision="2"
            :step="10"
            controls-position="right"
          />
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
import { computed, inject, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  createService,
  fetchServices,
  toggleServiceStatus,
  updateService
} from '@/api/serviceType.js'
import { fetchShop, updateShop } from '@/api/shop.js'
import { slicePage } from '@/utils/format.js'
import MoneyText from '@/components/common/MoneyText.vue'
import EmptyHint from '@/components/common/EmptyHint.vue'

const loading = ref(false)
const saving = ref(false)
const shopLoading = ref(false)
const shopSaving = ref(false)
const shop = ref({})
const shopForm = reactive({ shopName: '' })
const drawerVisible = ref(false)
const services = ref([])
const page = ref(1)
const size = 10
const formRef = ref()
const form = reactive({ id: '', name: '', price: 0 })
const rules = {
  name: [
    { required: true, message: '请输入服务名称', trigger: 'blur' },
    { max: 32, message: '服务名称最多32个字', trigger: 'blur' }
  ],
  price: [
    { required: true, message: '请输入价格', trigger: 'change' },
    {
      validator: (_r, v, cb) => {
        if (v === null || v === undefined || v === '') return cb(new Error('请输入价格'))
        if (Number(v) < 0) return cb(new Error('价格不能为负'))
        if (Number(v) > 99999999.99) return cb(new Error('价格过大'))
        cb()
      },
      trigger: 'change'
    }
  ]
}

const pagedServices = computed(() => slicePage(services.value, page.value, size))
const registerRefresh = inject('registerRefresh', null)
let unregister = null

async function loadShop() {
  shopLoading.value = true
  try {
    shop.value = (await fetchShop()) || {}
    shopForm.shopName = shop.value.shopName || ''
  } finally {
    shopLoading.value = false
  }
}

async function saveShop() {
  if (!shopForm.shopName?.trim()) {
    ElMessage.warning('请输入门店名称')
    return
  }
  shopSaving.value = true
  try {
    shop.value = await updateShop({ shopName: shopForm.shopName.trim() })
    ElMessage.success('门店资料已保存')
    try {
      const user = JSON.parse(sessionStorage.getItem('user') || '{}')
      user.shopName = shop.value.shopName
      sessionStorage.setItem('user', JSON.stringify(user))
    } catch {
      /* ignore */
    }
  } finally {
    shopSaving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    services.value = await fetchServices()
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.id = ''
  form.name = ''
  form.price = 0
}

function openCreate() {
  resetForm()
  drawerVisible.value = true
}

function openEdit(s) {
  form.id = s.id
  form.name = s.name
  form.price = Number(s.price)
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
    const payload = { name: form.name, price: form.price }
    if (form.id) {
      await updateService(form.id, payload)
      ElMessage.success('服务更新成功')
    } else {
      await createService(payload)
      ElMessage.success('服务创建成功')
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
      `确定${row.status === 'active' ? '停用' : '恢复'}服务「${row.name}」吗？`,
      '提示',
      { type: 'warning' }
    )
    await toggleServiceStatus(row.id)
    ElMessage.success('服务状态已更新')
    await load()
  } catch {
    // cancelled
  }
}

async function refreshAll() {
  await Promise.all([load(), loadShop()])
}

onMounted(async () => {
  await refreshAll()
  if (registerRefresh) unregister = registerRefresh(refreshAll)
})
onUnmounted(() => {
  if (unregister) unregister()
})
</script>

<style scoped>
.shop-card {
  margin-bottom: 16px;
  flex: 0 0 auto;
}
.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.quota-text {
  color: #64748b;
  font-size: 13px;
}
.shop-form {
  max-width: 480px;
}
</style>
