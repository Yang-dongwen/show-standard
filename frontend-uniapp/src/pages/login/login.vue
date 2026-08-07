<template>
  <view class="login-page">
    <view class="brand">
      <view class="brand-logo">S</view>
      <view class="brand-title">Show 门店</view>
      <view class="brand-sub">会员 · 收银 · 经营一站式</view>
    </view>

    <view class="form-card">
      <view class="form-title">{{ needBind ? '绑定店长账号' : '店长登录' }}</view>
      <view v-if="needBind" class="bind-tip">
        首次使用：微信已识别，请输入店长账号密码完成绑定
      </view>
      <view class="field-label">账号</view>
      <input
        class="field"
        v-model="username"
        placeholder="请输入用户名"
        placeholder-class="ph"
      />
      <view class="field-label">密码</view>
      <input
        class="field"
        v-model="password"
        password
        placeholder="请输入密码"
        placeholder-class="ph"
      />
      <PrimaryButton
        :loading="loading"
        :text="needBind ? '绑定并登录' : '登录'"
        @click="onLogin"
      />
      <!-- #ifdef MP-WEIXIN -->
      <view v-if="!needBind" class="wx-row">
        <PrimaryButton ghost text="微信一键登录" :loading="wxLoading" @click="onWxLogin" />
      </view>
      <view v-else class="wx-row">
        <PrimaryButton ghost text="取消绑定" @click="cancelBind" />
      </view>
      <!-- #endif -->
      <view class="tip">{{ productNote }}</view>
      <view class="tip tip-url">API: {{ displayBaseUrl }}</view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PrimaryButton from '@/components/PrimaryButton.vue'
import { productNote, baseUrl } from '@/utils/config'
import { getToken, saveSession } from '@/utils/auth'
import { login, wxLogin, wxBind } from '@/api/auth'

const username = ref('')
const password = ref('')
const loading = ref(false)
const wxLoading = ref(false)
const preToken = ref('')
const needBind = ref(false)

const displayBaseUrl = computed(() => {
  try {
    const override = uni.getStorageSync('baseUrlOverride')
    if (override) return String(override)
  } catch (_) {
    /* ignore */
  }
  return baseUrl
})

onShow(() => {
  if (getToken()) {
    uni.switchTab({ url: '/pages/home/home' })
  }
})

function cancelBind() {
  needBind.value = false
  preToken.value = ''
}

async function onLogin() {
  if (!username.value || !password.value) {
    uni.showToast({ title: '请输入账号和密码', icon: 'none' })
    return
  }
  loading.value = true
  try {
    if (needBind.value && preToken.value) {
      const data = await wxBind({
        preToken: preToken.value,
        username: username.value,
        password: password.value,
      })
      saveSession(data)
      needBind.value = false
      preToken.value = ''
      uni.showToast({ title: '绑定并登录成功', icon: 'success' })
    } else {
      const data = await login({
        username: username.value,
        password: password.value,
      })
      saveSession(data)
      uni.showToast({ title: '登录成功', icon: 'success' })
    }
    setTimeout(() => {
      uni.switchTab({ url: '/pages/home/home' })
    }, 400)
  } catch (e) {
    console.warn(e)
  } finally {
    loading.value = false
  }
}

async function onWxLogin() {
  // #ifdef MP-WEIXIN
  wxLoading.value = true
  try {
    const loginRes = await new Promise((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: resolve,
        fail: reject,
      })
    })
    const data = await wxLogin({ code: loginRes.code })
    if (data && data.token) {
      saveSession(data)
      uni.switchTab({ url: '/pages/home/home' })
      return
    }
    if (data && (data.bindRequired || data.preToken)) {
      needBind.value = true
      preToken.value = data.preToken || ''
      uni.showToast({ title: '请绑定店长账号', icon: 'none', duration: 2500 })
      return
    }
    uni.showToast({ title: '微信登录未返回会话', icon: 'none' })
  } catch (e) {
    console.warn(e)
  } finally {
    wxLoading.value = false
  }
  // #endif
  // #ifndef MP-WEIXIN
  uni.showToast({ title: '请在微信小程序中使用微信登录', icon: 'none' })
  // #endif
}
</script>

<style scoped lang="scss">
.login-page {
  min-height: 100vh;
  background: linear-gradient(165deg, #0f172a 0%, #1e1b4b 48%, #5b5ce2 100%);
  padding: 0 40rpx 60rpx;
  box-sizing: border-box;
}
.brand {
  padding: 160rpx 20rpx 60rpx;
  color: #fff;
  text-align: center;
}
.brand-logo {
  width: 112rpx;
  height: 112rpx;
  margin: 0 auto 24rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.15);
  border: 2rpx solid rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
  font-weight: 700;
  color: #fff;
}
.brand-title {
  font-size: 48rpx;
  font-weight: 700;
  letter-spacing: 2rpx;
}
.brand-sub {
  margin-top: 12rpx;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.72);
}
.form-card {
  background: #fff;
  border-radius: 28rpx;
  padding: 40rpx 36rpx 36rpx;
  box-shadow: 0 16rpx 48rpx rgba(15, 23, 42, 0.2);
}
.form-title {
  font-size: 34rpx;
  font-weight: 600;
  margin-bottom: 28rpx;
  color: #0f172a;
}
.bind-tip {
  margin: -12rpx 0 24rpx;
  padding: 16rpx 20rpx;
  background: rgba(91, 92, 226, 0.1);
  color: #4338ca;
  font-size: 24rpx;
  line-height: 1.5;
  border-radius: 12rpx;
}
.field-label {
  font-size: 24rpx;
  color: #64748b;
  margin-bottom: 10rpx;
}
.field {
  background: #f8fafc;
  border: 2rpx solid #e2e8f0;
  border-radius: 16rpx;
  padding: 22rpx 24rpx;
  font-size: 28rpx;
  margin-bottom: 24rpx;
}
.ph {
  color: #94a3b8;
}
.wx-row {
  margin-top: 20rpx;
}
.tip {
  margin-top: 28rpx;
  font-size: 22rpx;
  color: #94a3b8;
  line-height: 1.5;
  text-align: center;
}
.tip-url {
  margin-top: 8rpx;
  word-break: break-all;
}
</style>
