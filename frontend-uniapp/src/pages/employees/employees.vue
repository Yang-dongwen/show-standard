<template>
  <view class="page">
    <view class="card">
      <view class="card-title">{{ editingId ? '编辑员工' : '新增员工' }}</view>
      <input class="field" v-model="name" placeholder="员工姓名" maxlength="32" />
      <view class="btn-col">
        <PrimaryButton :loading="saving" :text="editingId ? '保存' : '添加'" @click="onSave" />
        <view v-if="editingId" class="cancel-wrap">
          <PrimaryButton ghost text="取消编辑" @click="cancelEdit" />
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">员工列表</view>
      <view v-if="loadError" class="error-tip">
        <text>{{ loadError }}</text>
        <text class="retry" @click="load">重试</text>
      </view>
      <view v-if="items.length">
        <view class="list-cell row" v-for="e in items" :key="e.id">
          <view class="cell-main">
            <view class="list-cell-title">{{ e.name }}</view>
            <view class="list-cell-desc">
              {{ isInactive(e) ? '停用' : '在岗' }}
            </view>
          </view>
          <view class="actions">
            <text class="link" @click="onEdit(e)">改名</text>
            <text class="link" @click="onToggle(e)">{{ isInactive(e) ? '启用' : '停用' }}</text>
          </view>
        </view>
      </view>
      <Empty v-else :text="loading ? '加载中…' : loadError ? '加载失败' : '暂无员工'" />
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { normalizePage } from '@/utils/request'
import {
  listEmployees,
  createEmployee,
  updateEmployee,
  toggleEmployeeStatus,
} from '@/api/employees'

const items = ref([])
const name = ref('')
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const editingId = ref(null)

function isInactive(e) {
  const s = e && e.status
  return s === 'inactive' || s === 0 || s === '0' || s === false
}

async function load() {
  if (!requireLogin()) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await listEmployees({ page: 1, size: 50 })
    items.value = normalizePage(data).items
  } catch (e) {
    loadError.value = (e && e.message) || '加载失败'
    items.value = []
  } finally {
    loading.value = false
  }
}

function cancelEdit() {
  editingId.value = null
  name.value = ''
}

function onEdit(e) {
  editingId.value = e.id
  name.value = e.name || ''
}

async function onSave() {
  if (!name.value || !name.value.trim()) {
    uni.showToast({ title: '请输入姓名', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = { name: name.value.trim() }
    if (editingId.value) {
      await updateEmployee(editingId.value, payload)
      uni.showToast({ title: '已更新', icon: 'success' })
    } else {
      await createEmployee(payload)
      uni.showToast({ title: '已添加', icon: 'success' })
    }
    cancelEdit()
    await load()
  } catch (e) {
    console.warn(e)
  } finally {
    saving.value = false
  }
}

async function onToggle(e) {
  const action = isInactive(e) ? '启用' : '停用'
  uni.showModal({
    title: action + '员工',
    content: '确认' + action + '「' + (e.name || '') + '」？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await toggleEmployeeStatus(e.id)
        uni.showToast({ title: '已' + action, icon: 'success' })
        await load()
      } catch (err) {
        console.warn(err)
      }
    },
  })
}

onShow(() => {
  load()
})
</script>

<style scoped lang="scss">
.btn-col {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.cancel-wrap {
  margin-top: 4rpx;
}
.row {
  align-items: center;
}
.cell-main {
  flex: 1;
  min-width: 0;
}
.actions {
  display: flex;
  gap: 20rpx;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.link {
  color: #5b5ce2;
  font-size: 26rpx;
}
.error-tip {
  margin-bottom: 16rpx;
  padding: 16rpx;
  background: #fef2f2;
  color: #ef4444;
  border-radius: 12rpx;
  font-size: 24rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.retry {
  color: #5b5ce2;
  margin-left: 16rpx;
  flex-shrink: 0;
}
</style>
