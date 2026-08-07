<template>
  <view class="page">
    <view class="card">
      <view class="card-title">{{ isEdit ? '编辑会员' : '新建会员' }}</view>

      <view class="field-label">姓名 *</view>
      <input class="field" v-model="form.name" maxlength="32" placeholder="不超过 32 字" />

      <view class="field-label">手机号 *</view>
      <input class="field" v-model="form.phone" maxlength="11" type="number" placeholder="1 开头 11 位" />

      <view class="field-label">校验码（可选 4 位）</view>
      <input class="field" v-model="form.verifyCode" maxlength="4" type="number" placeholder="消费时使用" />

      <view class="field-label">备注</view>
      <textarea class="field area" v-model="form.remark" maxlength="200" placeholder="不超过 200 字" />

      <view v-if="!isEdit" class="field-label">开卡充值（可选）</view>
      <input
        v-if="!isEdit"
        class="field"
        v-model="form.initialRechargeAmount"
        type="digit"
        placeholder="初始充值金额"
      />

      <PrimaryButton :loading="saving" :text="isEdit ? '保存修改' : '创建会员'" @click="onSave" />
      <view class="section-tip">编辑优先列表入参；后端无 GET /customers/{id} 时按 id 在 size≤200 内扫描补全。</view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { requireLogin } from '@/utils/auth'
import { createCustomer, updateCustomer, listCustomers } from '@/api/customers'
import { normalizePage } from '@/utils/request'

const isEdit = ref(false)
const customerId = ref(null)
const saving = ref(false)
const form = reactive({
  name: '',
  phone: '',
  verifyCode: '',
  remark: '',
  initialRechargeAmount: '',
})

onLoad(async (query) => {
  if (!requireLogin()) return
  if (query && query.id) {
    isEdit.value = true
    customerId.value = query.id
    form.name = decodeURIComponent(query.name || '')
    form.phone = decodeURIComponent(query.phone || '')
    form.verifyCode = decodeURIComponent(query.verifyCode || '')
    form.remark = decodeURIComponent(query.remark || '')
    // 无单条 GET：列表入参优先；缺字段时用 keyword/id 在 size≤200 内扫描补全
    const needScan = !form.name || !form.phone || !form.verifyCode || !form.remark
    if (needScan) {
      try {
        const data = await listCustomers({
          keyword: form.phone || form.name || '',
          page: 1,
          size: 200,
        })
        const { items } = normalizePage(data)
        let found = items.find((x) => String(x.id) === String(query.id))
        if (!found) {
          const all = await listCustomers({ page: 1, size: 200 })
          found = normalizePage(all).items.find((x) => String(x.id) === String(query.id))
        }
        if (found) {
          form.name = found.name || form.name
          form.phone = found.phone || form.phone
          if (!form.verifyCode) form.verifyCode = found.verifyCode || ''
          if (!form.remark) form.remark = found.remark || ''
        }
      } catch (e) {
        console.warn(e)
      }
    }
  }
})

function validate() {
  if (!form.name || form.name.length > 32) {
    uni.showToast({ title: '姓名必填且不超过 32 字', icon: 'none' })
    return false
  }
  if (!/^1\d{10}$/.test(form.phone || '')) {
    uni.showToast({ title: '手机号须为 1 开头 11 位', icon: 'none' })
    return false
  }
  if (form.verifyCode && !/^\d{4}$/.test(form.verifyCode)) {
    uni.showToast({ title: '校验码须为 4 位数字', icon: 'none' })
    return false
  }
  if (form.remark && form.remark.length > 200) {
    uni.showToast({ title: '备注不超过 200 字', icon: 'none' })
    return false
  }
  return true
}

async function onSave() {
  if (!validate()) return
  saving.value = true
  try {
    if (isEdit.value) {
      await updateCustomer(customerId.value, {
        name: form.name,
        phone: form.phone,
        verifyCode: form.verifyCode || undefined,
        remark: form.remark || undefined,
      })
    } else {
      const payload = {
        name: form.name,
        phone: form.phone,
        verifyCode: form.verifyCode || undefined,
        remark: form.remark || undefined,
      }
      if (form.initialRechargeAmount !== '' && form.initialRechargeAmount != null) {
        payload.initialRechargeAmount = Number(form.initialRechargeAmount)
      }
      await createCustomer(payload)
    }
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 400)
  } catch (e) {
    console.warn(e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped lang="scss">
.area {
  min-height: 140rpx;
  width: 100%;
  box-sizing: border-box;
}
</style>
