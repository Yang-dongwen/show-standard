<template>
  <view class="page">
    <view class="card">
      <view class="card-title">{{ editingId ? '编辑服务' : '新增服务' }}</view>
      <view class="field-label">名称</view>
      <input class="field" v-model="form.name" placeholder="服务名称" maxlength="64" />
      <view class="field-label">价格</view>
      <input class="field" v-model="form.price" type="digit" placeholder="默认价格" />
      <view class="btn-row">
        <PrimaryButton :loading="saving" :text="editingId ? '保存' : '添加'" @click="onSave" />
        <view v-if="editingId" class="cancel-wrap">
          <PrimaryButton ghost text="取消编辑" @click="cancelEdit" />
        </view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">服务项目</view>
      <view v-if="loadError" class="error-tip">
        <text>{{ loadError }}</text>
        <text class="retry" @click="load">重试</text>
      </view>
      <view v-if="items.length">
        <view class="list-cell row" v-for="s in items" :key="s.id">
          <view class="cell-main">
            <view class="list-cell-title">{{ s.name }}</view>
            <view class="list-cell-desc">
              ¥{{ money(s.price) }}
              · {{ isInactive(s) ? '停用' : '启用' }}
            </view>
          </view>
          <view class="actions">
            <text class="link" @click="onEdit(s)">编辑</text>
            <text class="link" @click="onToggle(s)">{{ isInactive(s) ? '启用' : '停用' }}</text>
          </view>
        </view>
      </view>
      <Empty v-else :text="loading ? '加载中…' : loadError ? '加载失败' : '暂无服务项目'" />
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { money } from '@/utils/format'
import { normalizePage } from '@/utils/request'
import {
  listServices,
  createService,
  updateService,
  toggleServiceStatus,
} from '@/api/services'

const items = ref([])
const loading = ref(false)
const saving = ref(false)
const loadError = ref('')
const editingId = ref(null)
const form = reactive({ name: '', price: '' })

function isInactive(s) {
  const st = s && s.status
  return st === 'inactive' || st === 0 || st === '0' || st === false
}

async function load() {
  if (!requireLogin()) return
  loading.value = true
  loadError.value = ''
  try {
    const data = await listServices({ page: 1, size: 50 })
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
  form.name = ''
  form.price = ''
}

function onEdit(s) {
  editingId.value = s.id
  form.name = s.name || ''
  form.price = s.price != null ? String(s.price) : ''
}

async function onSave() {
  if (!form.name || !form.name.trim()) {
    uni.showToast({ title: '请输入名称', icon: 'none' })
    return
  }
  const price = Number(form.price)
  if (form.price === '' || Number.isNaN(price) || price < 0) {
    uni.showToast({ title: '请输入有效价格', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const payload = { name: form.name.trim(), price }
    if (editingId.value) {
      await updateService(editingId.value, payload)
      uni.showToast({ title: '已更新', icon: 'success' })
    } else {
      await createService(payload)
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

async function onToggle(s) {
  const action = isInactive(s) ? '启用' : '停用'
  uni.showModal({
    title: action + '服务',
    content: `确认${action}「${s.name || ''}」？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await toggleServiceStatus(s.id)
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
.btn-row {
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
