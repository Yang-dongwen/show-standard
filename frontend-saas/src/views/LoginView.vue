<template>
  <div class="login-page">
    <div class="bg-orb orb-1" />
    <div class="bg-orb orb-2" />
    <div class="bg-orb orb-3" />

    <div class="login-shell">
      <section class="showcase">
        <div class="showcase-inner">
          <div class="logo-row">
            <div class="logo-mark">S</div>
            <span>Show SaaS</span>
          </div>
          <h1>运营中台<br /><em>管好每一家店</em></h1>
          <p class="lead">
            邀请开店、配额启停、到期续期、全网驾驶舱——与门店 C 端完全隔离的平台侧能力。
          </p>
          <ul class="bullets">
            <li>
              <span class="ico">01</span>
              <div>
                <strong>租户生命周期</strong>
                <small>开店 · 启停 · 配额 · 只读</small>
              </div>
            </li>
            <li>
              <span class="ico">02</span>
              <div>
                <strong>商业化续期</strong>
                <small>人工账单 · 到期拦截</small>
              </div>
            </li>
            <li>
              <span class="ico">03</span>
              <div>
                <strong>经营驾驶舱</strong>
                <small>全网 GMV · 风险门店</small>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <section class="form-panel">
        <div class="form-card">
          <div class="form-head">
            <h2>运营登录</h2>
            <p>使用平台账号进入 SaaS 控制台</p>
          </div>

          <el-form ref="formRef" :model="form" :rules="rules" size="large" @submit.prevent="onSubmit">
            <el-form-item prop="username">
              <el-input
                v-model="form.username"
                placeholder="运营账号"
                autocomplete="username"
                :prefix-icon="User"
              />
            </el-form-item>
            <el-form-item prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="密码"
                autocomplete="current-password"
                :prefix-icon="Lock"
              />
            </el-form-item>
            <el-button
              type="primary"
              class="submit"
              :loading="loading"
              native-type="submit"
            >
              进入中台
            </el-button>
          </el-form>

          <div class="hint-box">
            <div class="hint-label">开发示例账号</div>
            <code>platform</code>
            <span class="sep">/</span>
            <code>platform123</code>
          </div>

          <div class="links">
            <button type="button" class="link-btn" @click="$router.push('/open-shop')">
              商家邀请码开店 →
            </button>
          </div>
        </div>
        <p class="footer-note">与门店收银系统（C 端）数据共享、鉴权隔离</p>
      </section>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { saasLogin } from '@/api/http.js'

const router = useRouter()
const loading = ref(false)
const formRef = ref()
const form = reactive({ username: 'platform', password: '' })
const rules = {
  username: [{ required: true, message: '请输入运营账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  if (loading.value) return
  try {
    await formRef.value?.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await saasLogin(form)
    // localStorage：多标签页共享登录态
    localStorage.setItem('saasToken', data.token)
    localStorage.setItem('saasUser', JSON.stringify(data.user || {}))
    sessionStorage.removeItem('saasToken')
    sessionStorage.removeItem('saasUser')
    ElMessage.success('登录成功')
    router.push('/home/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  position: relative;
  overflow: hidden;
  background: #070b14;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 20px;
}

.bg-orb {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  pointer-events: none;
}
.orb-1 {
  width: 420px;
  height: 420px;
  background: rgba(99, 102, 241, 0.35);
  top: -80px;
  left: -60px;
}
.orb-2 {
  width: 360px;
  height: 360px;
  background: rgba(139, 92, 246, 0.28);
  bottom: -100px;
  right: 10%;
}
.orb-3 {
  width: 220px;
  height: 220px;
  background: rgba(6, 182, 212, 0.18);
  top: 40%;
  left: 40%;
}

.login-shell {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 980px;
  display: grid;
  grid-template-columns: 1.05fr 0.95fr;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 24px;
  overflow: hidden;
  box-shadow: 0 30px 80px rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(20px);
}

.showcase {
  padding: 48px 40px;
  background:
    linear-gradient(160deg, rgba(99, 102, 241, 0.18), transparent 55%),
    linear-gradient(180deg, rgba(15, 23, 42, 0.5), rgba(15, 23, 42, 0.2));
  color: #e2e8f0;
}

.logo-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-weight: 700;
  font-size: 14px;
  color: #c7d2fe;
  margin-bottom: 36px;
}
.logo-mark {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #a78bfa);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.4);
}

.showcase h1 {
  font-size: 34px;
  line-height: 1.25;
  font-weight: 750;
  letter-spacing: -0.03em;
  margin: 0 0 16px;
  color: #f8fafc;
}
.showcase h1 em {
  font-style: normal;
  background: linear-gradient(90deg, #a5b4fc, #c4b5fd, #67e8f9);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
.lead {
  font-size: 14px;
  line-height: 1.7;
  color: #94a3b8;
  max-width: 360px;
  margin-bottom: 32px;
}

.bullets {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.bullets li {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.ico {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 700;
  color: #a5b4fc;
  flex-shrink: 0;
}
.bullets strong {
  display: block;
  font-size: 13px;
  color: #e2e8f0;
}
.bullets small {
  display: block;
  margin-top: 2px;
  font-size: 12px;
  color: #64748b;
}

.form-panel {
  background: #fff;
  padding: 48px 40px 28px;
  display: flex;
  flex-direction: column;
}

.form-card { flex: 1; }
.form-head h2 {
  margin: 0;
  font-size: 24px;
  font-weight: 750;
  letter-spacing: -0.02em;
  color: #0f172a;
}
.form-head p {
  margin: 8px 0 28px;
  color: #64748b;
  font-size: 13px;
}

.submit {
  width: 100%;
  height: 44px;
  font-weight: 600;
  margin-top: 4px;
  border-radius: 10px !important;
}

.hint-box {
  margin-top: 22px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  font-size: 12px;
  color: #64748b;
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.hint-label {
  width: 100%;
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 2px;
}
.hint-box code {
  background: #eef2ff;
  color: #4338ca;
  padding: 2px 7px;
  border-radius: 6px;
  font-weight: 600;
  font-family: ui-monospace, Consolas, monospace;
}
.sep { color: #cbd5e1; }

.links {
  margin-top: 20px;
  text-align: center;
}
.link-btn {
  border: none;
  background: none;
  color: #6366f1;
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  padding: 4px;
}
.link-btn:hover { color: #4f46e5; }

.footer-note {
  margin-top: 24px;
  text-align: center;
  font-size: 11px;
  color: #94a3b8;
}

@media (max-width: 840px) {
  .login-shell {
    grid-template-columns: 1fr;
    max-width: 440px;
  }
  .showcase { display: none; }
  .form-panel { padding: 36px 28px 24px; }
}
</style>
