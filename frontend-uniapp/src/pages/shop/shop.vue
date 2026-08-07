<template>
  <view class="page">
    <view v-if="loadError" class="error-tip">
      <text>{{ loadError }}</text>
      <text class="retry" @click="load">重试</text>
    </view>

    <view class="card profile">
      <view class="avatar">店</view>
      <view class="info">
        <view class="name">{{ shopName || '门店' }}</view>
        <view class="user">{{ userLabel }}</view>
      </view>
    </view>

    <view class="card">
      <view class="card-title">门店资料</view>
      <view class="field-label">门店名称（≤64）</view>
      <input class="field" v-model="shopName" maxlength="64" placeholder="门店名称" />
      <view class="meta-line" v-if="tenantKey">门店码 {{ tenantKey }}</view>
      <view class="meta-line" v-if="quotaHint">配额 {{ quotaHint }}</view>
      <PrimaryButton :loading="saving" text="保存" @click="onSaveShop" />
    </view>

    <view class="card">
      <view class="card-title">功能入口</view>
      <view class="list-cell" @click="go('/pages/employees/employees')">
        <view class="list-cell-title">员工管理</view>
        <text class="list-cell-arrow">›</text>
      </view>
      <view class="list-cell" @click="go('/pages/services/services')">
        <view class="list-cell-title">服务项目</view>
        <text class="list-cell-arrow">›</text>
      </view>
      <view class="list-cell" @click="go('/pages/reports/reports')">
        <view class="list-cell-title">经营报表</view>
        <text class="list-cell-arrow">›</text>
      </view>
    </view>

    <!-- #ifdef MP-WEIXIN -->
    <view class="card">
      <view class="card-title">微信绑定</view>
      <view class="bind-status">
        状态：{{ wxBound === null ? '查询中…' : wxBound ? '已绑定' : '未绑定' }}
      </view>
      <PrimaryButton
        v-if="wxBound"
        ghost
        text="解绑微信"
        :loading="unbinding"
        @click="onUnbind"
      />
      <view class="section-tip">解绑后需重新微信登录并绑定店长账号</view>
    </view>
    <!-- #endif -->

    <view class="card">
      <PrimaryButton ghost text="退出登录" @click="onLogout" />
      <view class="section-tip">{{ productNote }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { requireLogin, getUser, clearSession } from '@/utils/auth'
import { productNote } from '@/utils/config'
import { getShop, updateShop } from '@/api/shop'
import { wxBindStatus, wxUnbind } from '@/api/auth'

const shopName = ref('')
const tenantKey = ref('')
const customerQuota = ref('')
const employeeQuota = ref('')
const loadError = ref('')
const saving = ref(false)
const wxBound = ref(null)
const unbinding = ref(false)

const userLabel = computed(() => {
  const u = getUser() || {}
  return u.username || u.name || u.displayName || '店长'
})

const quotaHint = computed(() => {
  const parts = []
  if (customerQuota.value) parts.push('会员 ' + customerQuota.value)
  if (employeeQuota.value) parts.push('员工 ' + employeeQuota.value)
  return parts.join(' · ')
})

function applyShop(data) {
  const d = data || {}
  shopName.value = d.shopName || d.name || ''
  tenantKey.value = d.tenantKey || d.tenant_key || ''
  customerQuota.value = d.customerQuota != null ? String(d.customerQuota) : ''
  employeeQuota.value = d.employeeQuota != null ? String(d.employeeQuota) : ''
}

async function load() {
  if (!requireLogin()) return
  loadError.value = ''
  try {
    const data = await getShop()
    applyShop(data)
  } catch (e) {
    loadError.value = (e && e.message) || '加载门店失败'
    console.warn(e)
  }
  // #ifdef MP-WEIXIN
  try {
    const st = await wxBindStatus()
    wxBound.value = !!(st && (st.bound === true || st.bound === 'true'))
  } catch (e) {
    wxBound.value = false
  }
  // #endif
}

async function onSaveShop() {
  const name = (shopName.value || '').trim()
  if (!name) {
    uni.showToast({ title: '请输入门店名称', icon: 'none' })
    return
  }
  if (name.length > 64) {
    uni.showToast({ title: '名称不超过 64 字', icon: 'none' })
    return
  }
  saving.value = true
  try {
    const data = await updateShop({ shopName: name })
    if (data) applyShop(data)
    uni.showToast({ title: '已保存', icon: 'success' })
  } catch (e) {
    console.warn(e)
  } finally {
    saving.value = false
  }
}

async function onUnbind() {
  unbinding.value = true
  try {
    await wxUnbind()
    wxBound.value = false
    uni.showToast({ title: '已解绑', icon: 'success' })
  } catch (e) {
    console.warn(e)
  } finally {
    unbinding.value = false
  }
}

function onLogout() {
  clearSession()
  uni.reLaunch({ url: '/pages/login/login' })
}

function go(url) {
  uni.navigateTo({ url })
}

onShow(() => {
  load()
})
</script>

<style scoped lang="scss">
.profile {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #5b5ce2, #7c3aed);
  color: #fff;
  font-size: 36rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.name {
  font-size: 34rpx;
  font-weight: 600;
  color: #0f172a;
}
.user {
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #64748b;
}
.meta-line {
  margin: 8rpx 0;
  font-size: 24rpx;
  color: #64748b;
}
.bind-status {
  font-size: 28rpx;
  color: #334155;
  margin-bottom: 20rpx;
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
