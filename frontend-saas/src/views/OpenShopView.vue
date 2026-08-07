<template>
  <div class="open-page">
    <div class="bg-grid" />
    <header class="top">
      <button type="button" class="back" @click="$router.push('/login')">
        ← 返回运营登录
      </button>
      <div class="brand">
        <span class="mark">S</span>
        Show · 商家开店
      </div>
    </header>

    <div class="wrap">
      <aside class="side">
        <h1>三分钟开通门店</h1>
        <p>使用运营发放的邀请码，创建店长账号。开店成功后请到 <strong>C 端门店系统</strong> 登录收银。</p>
        <ol class="steps">
          <li :class="{ done: !!form.inviteCode }"><span>1</span>填写邀请码</li>
          <li :class="{ done: !!form.username }"><span>2</span>设置店长账号</li>
          <li :class="{ done: !!result }"><span>3</span>到 C 端登录使用</li>
        </ol>
      </aside>

      <div class="card">
        <h2>邀请码开店</h2>
        <p class="sub">本页仅完成 SaaS 侧开通，不用于日常收银</p>

        <el-form
          v-if="!result"
          :model="form"
          label-position="top"
          size="large"
          @submit.prevent="onSubmit"
        >
          <el-form-item label="邀请码" required>
            <el-input v-model="form.inviteCode" placeholder="运营发放的邀请码" />
          </el-form-item>
          <el-form-item label="门店名称">
            <el-input v-model="form.shopName" placeholder="可选，默认用昵称生成" />
          </el-form-item>
          <div class="row2">
            <el-form-item label="店长用户名" required>
              <el-input v-model="form.username" autocomplete="username" />
            </el-form-item>
            <el-form-item label="昵称" required>
              <el-input v-model="form.nickname" maxlength="6" show-word-limit />
            </el-form-item>
          </div>
          <el-form-item label="登录密码" required>
            <el-input
              v-model="form.password"
              type="password"
              show-password
              autocomplete="new-password"
              placeholder="至少 6 位"
            />
          </el-form-item>
          <el-button type="primary" class="full" :loading="loading" native-type="submit">
            提交开店
          </el-button>
        </el-form>

        <div v-else class="success">
          <div class="success-icon">✓</div>
          <h3>开店成功</h3>
          <p class="shop">{{ result.shopName }}</p>
          <div class="kv">
            <div><span>门店码</span><code>{{ result.tenantKey }}</code></div>
            <div><span>店长账号</span><code>{{ result.username }}</code></div>
          </div>
          <p class="tip">请打开 C 端门店系统，使用店长账号登录开始收银。</p>
          <div class="actions">
            <el-button type="primary" @click="reset">再开一家</el-button>
            <el-button @click="$router.push('/login')">运营登录</el-button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { registerShop } from '@/api/http.js'

const loading = ref(false)
const result = ref(null)
const form = reactive({
  inviteCode: '',
  shopName: '',
  username: '',
  password: '',
  nickname: ''
})

async function onSubmit() {
  if (!form.inviteCode || !form.username || !form.password || !form.nickname) {
    ElMessage.warning('请填写必填项')
    return
  }
  loading.value = true
  result.value = null
  try {
    result.value = await registerShop({ ...form })
    ElMessage.success('开店成功')
  } catch (e) {
    ElMessage.error(e.message || '开店失败')
  } finally {
    loading.value = false
  }
}

function reset() {
  result.value = null
  form.inviteCode = ''
  form.shopName = ''
  form.username = ''
  form.password = ''
  form.nickname = ''
}
</script>

<style scoped>
.open-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef2ff 0%, #f8fafc 40%, #f1f5f9 100%);
  position: relative;
  overflow: hidden;
}
.bg-grid {
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(99, 102, 241, 0.06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(99, 102, 241, 0.06) 1px, transparent 1px);
  background-size: 32px 32px;
  mask-image: radial-gradient(ellipse 70% 60% at 50% 0%, #000 20%, transparent 75%);
  pointer-events: none;
}

.top {
  position: relative;
  z-index: 1;
  max-width: 960px;
  margin: 0 auto;
  padding: 24px 20px 8px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.back {
  border: none;
  background: none;
  color: #64748b;
  font-size: 13px;
  cursor: pointer;
  padding: 4px 0;
}
.back:hover { color: #4f46e5; }
.brand {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 700;
  font-size: 13px;
  color: #334155;
}
.mark {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
}

.wrap {
  position: relative;
  z-index: 1;
  max-width: 960px;
  margin: 24px auto 48px;
  padding: 0 20px;
  display: grid;
  grid-template-columns: 1fr 1.15fr;
  gap: 28px;
  align-items: start;
}

.side h1 {
  font-size: 28px;
  font-weight: 750;
  letter-spacing: -0.03em;
  color: #0f172a;
  margin: 12px 0 12px;
  line-height: 1.25;
}
.side p {
  font-size: 14px;
  color: #64748b;
  line-height: 1.7;
  margin-bottom: 28px;
}
.side strong { color: #4f46e5; }

.steps {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.steps li {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}
.steps li span {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #e2e8f0;
  color: #475569;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
}
.steps li.done { color: #4338ca; }
.steps li.done span {
  background: #6366f1;
  color: #fff;
}

.card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 32px 28px;
  box-shadow: 0 16px 40px rgba(15, 23, 42, 0.06);
}
.card h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
}
.sub {
  margin: 6px 0 24px;
  font-size: 13px;
  color: #94a3b8;
}
.row2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.full { width: 100%; height: 44px; font-weight: 600; border-radius: 10px !important; }

.success {
  text-align: center;
  padding: 12px 0 4px;
}
.success-icon {
  width: 56px;
  height: 56px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: #ecfdf5;
  color: #059669;
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
}
.success h3 {
  margin: 0;
  font-size: 20px;
}
.shop {
  margin: 8px 0 20px;
  font-size: 15px;
  font-weight: 600;
  color: #4f46e5;
}
.kv {
  display: flex;
  flex-direction: column;
  gap: 10px;
  text-align: left;
  background: #f8fafc;
  border-radius: 12px;
  padding: 14px 16px;
  margin-bottom: 16px;
}
.kv div {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #64748b;
}
.kv code {
  font-family: ui-monospace, Consolas, monospace;
  font-weight: 700;
  color: #0f172a;
  background: #fff;
  padding: 4px 8px;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}
.tip {
  font-size: 13px;
  color: #64748b;
  line-height: 1.5;
  margin-bottom: 20px;
}
.actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

@media (max-width: 800px) {
  .wrap { grid-template-columns: 1fr; }
  .side { order: 2; }
  .row2 { grid-template-columns: 1fr; }
}
</style>
