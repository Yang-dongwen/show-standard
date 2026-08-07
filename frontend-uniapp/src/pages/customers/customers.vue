<template>
  <view class="page">
    <SearchBar v-model="keyword" placeholder="搜索姓名/手机号" @search="onSearch" />
    <view class="toolbar">
      <PrimaryButton text="新建会员" @click="goEdit()" />
    </view>

    <view v-if="loadError" class="error-tip">
      <text>{{ loadError }}</text>
      <text class="retry" @click="fetchList(true)">重试</text>
    </view>
    <view class="card" v-if="items.length">
      <view
        class="list-cell row"
        v-for="c in items"
        :key="c.id"
      >
        <view class="cell-main" @click="goEdit(c)">
          <view class="list-cell-title">{{ c.name }}</view>
          <view class="list-cell-desc">
            {{ c.phone || '—' }} · 余额 ¥{{ money(c.balance) }} · {{ statusText(c) }}
          </view>
        </view>
        <view class="actions">
          <text class="link" @click.stop="onToggle(c)">{{ isInactive(c) ? '启用' : '停用' }}</text>
          <text class="list-cell-arrow" @click="goEdit(c)">›</text>
        </view>
      </view>
    </view>
    <Empty
      v-else
      :text="loading ? '加载中…' : loadError ? '加载失败' : '暂无会员，点击上方新建'"
    />

    <view v-if="hasMore" class="more" @click="loadMore">
      {{ loading ? '加载中…' : '加载更多' }}
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import SearchBar from '@/components/SearchBar.vue'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { money } from '@/utils/format'
import { normalizePage } from '@/utils/request'
import { listCustomers, toggleCustomerStatus } from '@/api/customers'

const keyword = ref('')
const items = ref([])
const page = ref(1)
const size = 20
const total = ref(0)
const loading = ref(false)
const hasMore = ref(false)
const loadError = ref('')

function isInactive(c) {
  const s = c && c.status
  return s === 'inactive' || s === 0 || s === '0' || s === false
}

function statusText(c) {
  return isInactive(c) ? '停用' : '正常'
}

async function fetchList(reset = false) {
  if (!requireLogin()) return
  if (loading.value) return
  loading.value = true
  if (reset) loadError.value = ''
  try {
    if (reset) {
      page.value = 1
      items.value = []
    }
    const data = await listCustomers({
      keyword: keyword.value || undefined,
      page: page.value,
      size,
    })
    const norm = normalizePage(data)
    items.value = reset ? norm.items : items.value.concat(norm.items)
    total.value = norm.total
    hasMore.value = items.value.length < total.value
    loadError.value = ''
  } catch (e) {
    if (reset || !items.value.length) {
      loadError.value = (e && e.message) || '加载失败'
    }
    console.warn(e)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  fetchList(true)
}

function loadMore() {
  if (!hasMore.value || loading.value) return
  page.value += 1
  fetchList(false)
}

function goEdit(c) {
  if (c && c.id) {
    const q = [
      'id=' + c.id,
      'name=' + encodeURIComponent(c.name || ''),
      'phone=' + encodeURIComponent(c.phone || ''),
      'verifyCode=' + encodeURIComponent(c.verifyCode || ''),
      'remark=' + encodeURIComponent(c.remark || ''),
    ].join('&')
    uni.navigateTo({ url: '/pages/customer-edit/customer-edit?' + q })
  } else {
    uni.navigateTo({ url: '/pages/customer-edit/customer-edit' })
  }
}

async function onToggle(c) {
  if (!c || !c.id) return
  const action = isInactive(c) ? '启用' : '停用'
  uni.showModal({
    title: action + '会员',
    content: '确认' + action + '「' + (c.name || '') + '」？',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await toggleCustomerStatus(c.id)
        uni.showToast({ title: '已' + action, icon: 'success' })
        await fetchList(true)
      } catch (err) {
        console.warn(err)
      }
    },
  })
}

onShow(() => {
  fetchList(true)
})
</script>

<style scoped lang="scss">
.toolbar {
  margin-bottom: 20rpx;
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
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.link {
  color: #5b5ce2;
  font-size: 26rpx;
}
.more {
  text-align: center;
  color: #5b5ce2;
  font-size: 26rpx;
  padding: 24rpx;
}
.error-tip {
  margin-bottom: 16rpx;
  padding: 16rpx 20rpx;
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
