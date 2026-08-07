<template>
  <view class="page">
    <view class="mode-tabs">
      <view :class="['tab', mode === 'consume' && 'on']" @click="mode = 'consume'">消费</view>
      <view :class="['tab', mode === 'recharge' && 'on']" @click="mode = 'recharge'">充值</view>
    </view>

    <view class="card">
      <view class="card-title">{{ mode === 'consume' ? '消费收银' : '会员充值' }}</view>

      <view class="field-label">会员 *</view>
      <view class="picker-row" @click="openCustomerPicker">
        <text>{{ selectedCustomer ? selectedCustomer.name + ' ' + (selectedCustomer.phone || '') : '选择会员（可搜索）' }}</text>
        <text class="arrow">›</text>
      </view>

      <template v-if="mode === 'consume'">
        <view class="field-label">员工 *</view>
        <view class="picker-row" @click="openEmployeePicker">
          <text>{{ selectedEmployee ? selectedEmployee.name : '选择员工' }}</text>
          <text class="arrow">›</text>
        </view>
        <view class="field-label">服务项目 *</view>
        <view class="picker-row" @click="openServicePicker">
          <text>{{ selectedService ? selectedService.name + ' ¥' + money(selectedService.price) : '选择服务' }}</text>
          <text class="arrow">›</text>
        </view>
        <view class="field-label">校验码（4 位）*</view>
        <input class="field" v-model="verifyCode" maxlength="4" type="number" placeholder="会员校验码" />
      </template>

      <view class="field-label">金额 *</view>
      <input class="field" v-model="amount" type="digit" placeholder="请输入金额" />

      <view class="field-label">备注</view>
      <input class="field" v-model="remark" placeholder="可选" />

      <PrimaryButton :loading="submitting" :text="mode === 'consume' ? '确认消费' : '确认充值'" @click="onSubmit" />
    </view>

    <view class="card">
      <view class="card-title">近流水</view>
      <view v-if="bootError" class="error-tip">
        <text>{{ bootError }}</text>
        <text class="retry" @click="bootstrap">重试</text>
      </view>
      <view v-if="txns.length" class="txn-list">
        <view class="txn" v-for="t in txns" :key="t.id">
          <view class="txn-top">
            <text class="txn-type">{{ typeLabel(t) }}</text>
            <text :class="['txn-status', statusClass(t)]">{{ statusLabel(t) }}</text>
          </view>
          <view class="txn-mid">
            <text>{{ t.customerName || t.customer_name || '会员' }}</text>
            <Money :value="t.amount" :primary="isRecharge(t)" />
          </view>
          <view class="txn-detail">{{ t.detail || t.remark || '—' }}</view>
        </view>
      </view>
      <Empty v-else :text="booting ? '加载中…' : bootError ? '加载失败' : '暂无流水'" />
    </view>

    <!-- 简易选择弹层 -->
    <view v-if="pickerVisible" class="mask" @click="pickerVisible = false">
      <view class="sheet" @click.stop>
        <view class="sheet-title">{{ pickerTitle }}</view>
        <input class="field" v-model="pickerKeyword" :placeholder="'搜索' + pickerTitle" @input="onPickerSearch" />
        <scroll-view scroll-y class="sheet-list">
          <view
            class="sheet-item"
            v-for="item in pickerList"
            :key="item.id"
            @click="onPick(item)"
          >
            {{ item.name }}{{ item.phone ? ' · ' + item.phone : '' }}{{ item.price != null ? ' · ¥' + money(item.price) : '' }}
          </view>
          <Empty v-if="!pickerList.length" text="无匹配项" />
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import Money from '@/components/Money.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { money } from '@/utils/format'
import { normalizePage } from '@/utils/request'
import { listCustomers } from '@/api/customers'
import { employeeOptions } from '@/api/employees'
import { serviceOptions } from '@/api/services'
import { listTransactions, recharge, consume } from '@/api/transactions'

const mode = ref('consume')
const selectedCustomer = ref(null)
const selectedEmployee = ref(null)
const selectedService = ref(null)
const verifyCode = ref('')
const amount = ref('')
const remark = ref('')
const submitting = ref(false)
const txns = ref([])
const booting = ref(false)
const bootError = ref('')

const customers = ref([])
const employees = ref([])
const services = ref([])

const pickerVisible = ref(false)
const pickerType = ref('') // customer | employee | service
const pickerTitle = ref('')
const pickerKeyword = ref('')
const pickerList = ref([])

async function bootstrap() {
  if (!requireLogin()) return
  booting.value = true
  bootError.value = ''
  try {
    const results = await Promise.all([
      listCustomers({ page: 1, size: 50 }).catch((err) => ({ __err: err })),
      employeeOptions().catch((err) => ({ __err: err })),
      serviceOptions().catch((err) => ({ __err: err })),
      listTransactions({ page: 1, size: 15 }).catch((err) => ({ __err: err })),
    ])
    const [c, e, s, t] = results
    const fails = []
    if (c && c.__err) fails.push('会员')
    else customers.value = normalizePage(c).items
    if (e && e.__err) fails.push('员工')
    else employees.value = Array.isArray(e) ? e : normalizePage(e).items
    if (s && s.__err) fails.push('服务')
    else services.value = Array.isArray(s) ? s : normalizePage(s).items
    if (t && t.__err) {
      fails.push('流水')
      txns.value = []
    } else {
      txns.value = normalizePage(t).items
    }
    if (fails.length) {
      bootError.value = fails.join('、') + '加载失败'
    }
  } catch (err) {
    bootError.value = (err && err.message) || '加载失败'
    console.warn(err)
  } finally {
    booting.value = false
  }
}

function openCustomerPicker() {
  pickerType.value = 'customer'
  pickerTitle.value = '会员'
  pickerKeyword.value = ''
  pickerList.value = customers.value
  pickerVisible.value = true
  // 远程再拉一页
  listCustomers({ page: 1, size: 50, keyword: '' })
    .then((d) => {
      customers.value = normalizePage(d).items
      if (pickerType.value === 'customer') pickerList.value = customers.value
    })
    .catch(() => {})
}

function openEmployeePicker() {
  pickerType.value = 'employee'
  pickerTitle.value = '员工'
  pickerKeyword.value = ''
  pickerList.value = employees.value
  pickerVisible.value = true
}

function openServicePicker() {
  pickerType.value = 'service'
  pickerTitle.value = '服务'
  pickerKeyword.value = ''
  pickerList.value = services.value
  pickerVisible.value = true
}

let searchTimer = null
function onPickerSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(async () => {
    const kw = (pickerKeyword.value || '').trim()
    if (pickerType.value === 'customer') {
      try {
        const d = await listCustomers({ page: 1, size: 50, keyword: kw || undefined })
        pickerList.value = normalizePage(d).items
      } catch (e) {
        pickerList.value = customers.value.filter(
          (x) =>
            !kw ||
            (x.name && x.name.includes(kw)) ||
            (x.phone && String(x.phone).includes(kw))
        )
      }
    } else if (pickerType.value === 'employee') {
      pickerList.value = employees.value.filter((x) => !kw || (x.name && x.name.includes(kw)))
    } else {
      pickerList.value = services.value.filter((x) => !kw || (x.name && x.name.includes(kw)))
    }
  }, 250)
}

function onPick(item) {
  if (pickerType.value === 'customer') selectedCustomer.value = item
  if (pickerType.value === 'employee') selectedEmployee.value = item
  if (pickerType.value === 'service') {
    selectedService.value = item
    if (item.price != null && mode.value === 'consume') {
      amount.value = String(item.price)
    }
  }
  pickerVisible.value = false
}

async function onSubmit() {
  if (!selectedCustomer.value) {
    uni.showToast({ title: '请选择会员', icon: 'none' })
    return
  }
  const amt = Number(amount.value)
  if (!amt || amt <= 0) {
    uni.showToast({ title: '请输入有效金额', icon: 'none' })
    return
  }
  submitting.value = true
  try {
    if (mode.value === 'recharge') {
      await recharge({
        customerId: selectedCustomer.value.id,
        amount: amt,
        remark: remark.value || undefined,
      })
    } else {
      if (!selectedEmployee.value) {
        uni.showToast({ title: '请选择员工', icon: 'none' })
        submitting.value = false
        return
      }
      if (!selectedService.value) {
        uni.showToast({ title: '请选择服务', icon: 'none' })
        submitting.value = false
        return
      }
      if (!/^\d{4}$/.test(verifyCode.value || '')) {
        uni.showToast({ title: '请输入 4 位校验码', icon: 'none' })
        submitting.value = false
        return
      }
      await consume({
        customerId: selectedCustomer.value.id,
        employeeId: selectedEmployee.value.id,
        serviceTypeId: selectedService.value.id,
        verifyCode: verifyCode.value,
        amount: amt,
        remark: remark.value || undefined,
      })
    }
    uni.showToast({ title: '成功', icon: 'success' })
    amount.value = ''
    remark.value = ''
    verifyCode.value = ''
    const t = await listTransactions({ page: 1, size: 15 })
    txns.value = normalizePage(t).items
  } catch (e) {
    console.warn(e)
  } finally {
    submitting.value = false
  }
}

function isRecharge(t) {
  const ty = (t.type || t.txnType || t.transactionType || '').toString().toLowerCase()
  return ty.includes('recharge') || ty.includes('充值')
}

function typeLabel(t) {
  if (isRecharge(t)) return '充值'
  return '消费'
}

function statusLabel(t) {
  const s = (t.status || '').toString().toLowerCase()
  const detail = (t.detail || '').toString()
  if (s === 'reversed' || detail.includes('冲正') || detail.startsWith('[冲正]')) return '已冲正'
  if (s === 'reversal') return '冲正单'
  if (s === 'normal' || !s) return '正常'
  return t.status
}

function statusClass(t) {
  const s = (t.status || '').toString().toLowerCase()
  const detail = (t.detail || '').toString()
  if (s === 'reversed' || s === 'reversal' || detail.includes('冲正')) return 'warn'
  return 'ok'
}

onShow(() => {
  bootstrap()
})
</script>

<style scoped lang="scss">
.mode-tabs {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  padding: 8rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(15, 23, 42, 0.04);
}
.tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #64748b;
}
.tab.on {
  background: #5b5ce2;
  color: #fff;
  font-weight: 600;
}
.picker-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 22rpx 24rpx;
  margin-bottom: 20rpx;
  font-size: 28rpx;
  color: #0f172a;
}
.arrow {
  color: #cbd5e1;
}
.txn {
  padding: 20rpx 0;
  border-bottom: 1rpx solid #f1f5f9;
}
.txn:last-child {
  border-bottom: none;
}
.txn-top {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8rpx;
}
.txn-type {
  font-weight: 600;
  color: #0f172a;
}
.txn-status {
  font-size: 22rpx;
  padding: 2rpx 12rpx;
  border-radius: 999rpx;
}
.txn-status.ok {
  background: rgba(7, 193, 96, 0.12);
  color: #07c160;
}
.txn-status.warn {
  background: rgba(245, 158, 11, 0.15);
  color: #d97706;
}
.txn-mid {
  display: flex;
  justify-content: space-between;
  font-size: 28rpx;
}
.txn-detail {
  margin-top: 6rpx;
  font-size: 22rpx;
  color: #94a3b8;
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
.mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(15, 23, 42, 0.45);
  z-index: 1000;
  display: flex;
  align-items: flex-end;
}
.sheet {
  width: 100%;
  background: #fff;
  border-radius: 24rpx 24rpx 0 0;
  padding: 28rpx 28rpx 48rpx;
  max-height: 70vh;
  box-sizing: border-box;
}
.sheet-title {
  font-size: 32rpx;
  font-weight: 600;
  margin-bottom: 16rpx;
}
.sheet-list {
  max-height: 50vh;
}
.sheet-item {
  padding: 24rpx 8rpx;
  border-bottom: 1rpx solid #f1f5f9;
  font-size: 28rpx;
}
</style>
