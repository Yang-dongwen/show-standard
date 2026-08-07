<template>
  <view class="page">
    <view class="hero card">
      <view class="hero-name">{{ shopName || '我的门店' }}</view>
      <view class="hero-meta">
        <text v-if="tenantKey">门店码 {{ tenantKey }}</text>
        <text v-if="tenantKey && planCode" class="dot">·</text>
        <text v-if="planCode">{{ planCode }}</text>
      </view>
      <view v-if="quotaHint" class="hero-quota">{{ quotaHint }}</view>
    </view>

    <view v-if="error" class="error-tip">
      <text>{{ error }}</text>
      <text class="retry" @click="load">重试</text>
    </view>

    <view class="stat-grid" v-if="loaded && !error">
      <view class="stat-item" v-for="item in stats" :key="item.label">
        <StatCard :label="item.label" :primary="item.primary">
          <Money v-if="item.money" :value="item.value" primary />
          <text v-else>{{ item.value }}</text>
        </StatCard>
      </view>
    </view>

    <view class="card">
      <view class="card-title">快捷入口</view>
      <view class="shortcuts">
        <view class="sc" @click="goTab('/pages/customers/customers')">会员</view>
        <view class="sc" @click="goTab('/pages/transactions/transactions')">收银</view>
        <view class="sc" @click="goPage('/pages/reports/reports')">报表</view>
        <view class="sc" @click="goPage('/pages/employees/employees')">员工</view>
        <view class="sc" @click="goPage('/pages/services/services')">服务</view>
        <view class="sc" @click="goTab('/pages/shop/shop')">我的</view>
      </view>
    </view>

    <Empty v-if="!loaded && !error" text="加载中…" icon="⏳" />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow, onPullDownRefresh } from '@dcloudio/uni-app'
import StatCard from '@/components/StatCard.vue'
import Money from '@/components/Money.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { pickField } from '@/utils/format'
import { dashboard } from '@/api/reports'

const loaded = ref(false)
const error = ref('')
const shopName = ref('')
const tenantKey = ref('')
const planCode = ref('')
const activeCustomers = ref(0)
const totalBalance = ref(0)
const todayRecharge = ref(0)
const todayConsume = ref(0)
const quotaHint = ref('')

const stats = computed(() => [
  { label: '在籍会员', value: activeCustomers.value, money: false, primary: true },
  { label: '会员余额', value: totalBalance.value, money: true, primary: true },
  { label: '今日充值', value: todayRecharge.value, money: true, primary: false },
  { label: '今日消费', value: todayConsume.value, money: true, primary: false },
])

function formatQuota(data) {
  const cq = pickField(
    data,
    'customerQuota',
    'customer_quota',
    'memberQuota',
    'member_quota'
  )
  const eq = pickField(data, 'employeeQuota', 'employee_quota')
  const parts = []
  if (cq != null && cq !== '') parts.push('会员 ' + cq)
  if (eq != null && eq !== '') parts.push('员工 ' + eq)
  if (parts.length) return '配额 ' + parts.join(' / ')
  const q = pickField(data, 'quota')
  return q != null && q !== '' ? String(q) : ''
}

async function load() {
  if (!requireLogin()) return
  error.value = ''
  try {
    const data = await dashboard()
    shopName.value = pickField(data, 'shopName', 'shop_name') || '我的门店'
    tenantKey.value = pickField(data, 'tenantKey', 'tenant_key') || ''
    planCode.value = pickField(data, 'planCode', 'plan_code') || ''
    activeCustomers.value = Number(pickField(data, 'activeCustomers', 'active_customers') || 0)
    totalBalance.value = Number(pickField(data, 'totalBalance', 'total_balance') || 0)
    todayRecharge.value = Number(pickField(data, 'todayRecharge', 'today_recharge') || 0)
    todayConsume.value = Number(pickField(data, 'todayConsume', 'today_consume') || 0)
    quotaHint.value = formatQuota(data || {})
    loaded.value = true
  } catch (e) {
    error.value = (e && e.message) || '加载失败'
    loaded.value = true
  }
}

function goTab(url) {
  uni.switchTab({ url })
}
function goPage(url) {
  uni.navigateTo({ url })
}

onShow(() => {
  load()
})

onPullDownRefresh(async () => {
  await load()
  uni.stopPullDownRefresh()
})
</script>

<style scoped lang="scss">
.hero-name {
  font-size: 36rpx;
  font-weight: 700;
  color: #0f172a;
}
.hero-meta {
  margin-top: 8rpx;
  font-size: 24rpx;
  color: #64748b;
}
.hero-quota {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #64748b;
}
.dot {
  margin: 0 8rpx;
}
.stat-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -10rpx 8rpx;
}
.stat-item {
  width: 50%;
  box-sizing: border-box;
  padding: 10rpx;
}
.shortcuts {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}
.sc {
  width: calc(33.33% - 12rpx);
  box-sizing: border-box;
  text-align: center;
  background: rgba(91, 92, 226, 0.08);
  color: #5b5ce2;
  border-radius: 16rpx;
  padding: 24rpx 0;
  font-size: 28rpx;
  font-weight: 500;
}
.error-tip {
  margin-bottom: 16rpx;
  padding: 20rpx;
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
