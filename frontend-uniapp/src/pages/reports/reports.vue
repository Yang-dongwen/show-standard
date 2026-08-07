<template>
  <view class="page">
    <view class="card">
      <view class="card-title">日期区间</view>
      <view class="date-row">
        <picker mode="date" :value="startDate" @change="onStart">
          <view class="date-box">起 {{ startDate }}</view>
        </picker>
        <text class="sep">至</text>
        <picker mode="date" :value="endDate" @change="onEnd">
          <view class="date-box">止 {{ endDate }}</view>
        </picker>
      </view>
      <PrimaryButton text="查询" @click="load" />
      <view class="section-tip">默认近 7 日；字段兼容 snake_case / camelCase</view>
    </view>

    <view v-if="pageError" class="error-tip">
      <text>{{ pageError }}</text>
      <text class="retry" @click="load">重试</text>
    </view>

    <view class="card">
      <view class="card-title">经营汇总</view>
      <view class="stat-grid">
        <view class="stat-item">
          <StatCard label="充值合计" primary>
            <Money :value="summaryData.totalRecharge" primary />
          </StatCard>
        </view>
        <view class="stat-item">
          <StatCard label="消费合计">
            <Money :value="summaryData.totalConsume" />
          </StatCard>
        </view>
      </view>
      <view v-if="summaryFailed" class="inline-warn">汇总接口失败，数字可能不准</view>
    </view>

    <view class="card">
      <view class="card-title">员工业绩</view>
      <view v-if="performance.length">
        <view class="list-cell" v-for="(p, idx) in performance" :key="p.employeeId || p.employee_id || idx">
          <view>
            <view class="list-cell-title">{{ p.employeeName || p.employee_name || '员工' }}</view>
            <view class="list-cell-desc">
              笔数 {{ p.orderCount || p.order_count || p.count || 0 }}
            </view>
          </view>
          <Money :value="p.amount || p.totalAmount || p.total_amount || 0" primary />
        </view>
      </view>
      <Empty
        v-else
        :text="loading ? '加载中…' : perfFailed ? '业绩加载失败' : '暂无业绩数据'"
      />
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import StatCard from '@/components/StatCard.vue'
import Money from '@/components/Money.vue'
import Empty from '@/components/Empty.vue'
import { requireLogin } from '@/utils/auth'
import { today, daysAgo, pickField } from '@/utils/format'
import { summary, employeePerformance } from '@/api/reports'

const startDate = ref(daysAgo(7))
const endDate = ref(today())
const loading = ref(false)
const performance = ref([])
const pageError = ref('')
const summaryFailed = ref(false)
const perfFailed = ref(false)
const summaryData = reactive({
  totalRecharge: 0,
  totalConsume: 0,
})

function onStart(e) {
  startDate.value = e.detail.value
}
function onEnd(e) {
  endDate.value = e.detail.value
}

async function load() {
  if (!requireLogin()) return
  loading.value = true
  pageError.value = ''
  summaryFailed.value = false
  perfFailed.value = false
  try {
    const params = { startDate: startDate.value, endDate: endDate.value }
    const [sum, perf] = await Promise.all([
      summary(params).catch((err) => ({ __err: err })),
      employeePerformance(params).catch((err) => ({ __err: err })),
    ])
    if (sum && sum.__err) {
      summaryFailed.value = true
      summaryData.totalRecharge = 0
      summaryData.totalConsume = 0
    } else if (sum) {
      summaryData.totalRecharge = Number(
        pickField(sum, 'totalRecharge', 'total_recharge', 'rechargeTotal') || 0
      )
      summaryData.totalConsume = Number(
        pickField(sum, 'totalConsume', 'total_consume', 'consumeTotal') || 0
      )
    }
    if (perf && perf.__err) {
      perfFailed.value = true
      performance.value = []
    } else if (Array.isArray(perf)) {
      performance.value = perf
    } else if (perf && Array.isArray(perf.items)) {
      performance.value = perf.items
    } else if (perf && Array.isArray(perf.list)) {
      performance.value = perf.list
    } else {
      performance.value = []
    }
    if (summaryFailed.value && perfFailed.value) {
      pageError.value = '报表加载失败'
    } else if (summaryFailed.value || perfFailed.value) {
      pageError.value = summaryFailed.value ? '汇总加载失败' : '业绩加载失败'
    }
  } catch (e) {
    pageError.value = (e && e.message) || '加载失败'
    console.warn(e)
  } finally {
    loading.value = false
  }
}

onShow(() => {
  load()
})
</script>

<style scoped lang="scss">
.date-row {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  gap: 12rpx;
}
.date-box {
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 12rpx;
  padding: 16rpx 20rpx;
  font-size: 26rpx;
  color: #0f172a;
}
.sep {
  color: #94a3b8;
  font-size: 24rpx;
}
.stat-grid {
  display: flex;
  flex-wrap: wrap;
  margin: 0 -10rpx;
}
.stat-item {
  width: 50%;
  box-sizing: border-box;
  padding: 10rpx;
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
.inline-warn {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #d97706;
}
</style>
