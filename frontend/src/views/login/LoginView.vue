<template>
  <div class="login-page">
    <div class="login-brand">
      <div class="brand-content">
        <div class="brand-logo">
          <img src="@/assets/logo.png" alt="Show" width="72" />
        </div>
        <h1 class="brand-name">Show</h1>
        <p class="brand-slogan">剪发更专注，管店更轻松</p>
        <div class="brand-features">
          <div class="feature-item"><span class="dot" />留住每一位顾客</div>
          <div class="feature-item"><span class="dot" />激活每一笔消费</div>
          <div class="feature-item"><span class="dot" />锁客源，更锁人心</div>
        </div>
      </div>
    </div>

    <div class="login-form-area">
      <div class="login-card">
        <div class="card-header">
          <h2>欢迎回来</h2>
          <p>登录店长 / 店员账号，管理会员与收银</p>
        </div>

        <el-form
          ref="loginFormRef"
          :model="loginForm"
          :rules="loginRules"
          size="large"
          @submit.prevent="handleLogin"
        >
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              class="field-full"
              placeholder="用户名"
              :prefix-icon="User"
              autocomplete="username"
            />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              class="field-full"
              type="password"
              show-password
              placeholder="密码"
              :prefix-icon="Lock"
              autocomplete="current-password"
            />
          </el-form-item>
          <el-form-item class="options-row">
            <el-checkbox v-model="rememberMe">记住用户名</el-checkbox>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" class="full-btn" :loading="loading" native-type="submit">
              登录
            </el-button>
          </el-form-item>
          <el-form-item v-if="registerAllowed">
            <el-button class="full-btn" plain :disabled="loading" @click="openRegister">
              注册店长账号
            </el-button>
          </el-form-item>
          <p v-else class="register-tip">当前不允许开放注册，请使用已有账号登录</p>
        </el-form>
      </div>
    </div>

    <el-dialog
      v-model="registerVisible"
      title="注册店长账号"
      width="420px"
      align-center
      destroy-on-close
      @closed="resetRegister"
    >
      <p class="dialog-tip">创建后可直接登录使用</p>
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="left"
        label-width="80px"
      >
        <el-form-item label="用户名" prop="username">
          <el-input
            v-model="registerForm.username"
            class="field-md"
            placeholder="字母开头，仅字母/数字/下划线"
          />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="registerForm.password" class="field-md" type="password" show-password placeholder="至少6位" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="registerForm.confirmPassword" class="field-md" type="password" show-password placeholder="再次输入" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="registerForm.nickname" class="field-sm" maxlength="6" show-word-limit placeholder="最多6字" />
        </el-form-item>
        <el-form-item v-if="requireInviteCode" label="邀请码" prop="inviteCode">
          <el-input v-model="registerForm.inviteCode" class="field-md" placeholder="本地静态邀请码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="registering" @click="registerVisible = false">取消</el-button>
        <el-button type="primary" :loading="registering" @click="handleRegister">确认注册</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { fetchRegisterStatus, login, register } from '@/api/auth.js'

const REMEMBER_KEY = 'show.remember.username'

const router = useRouter()
const loading = ref(false)
const registering = ref(false)
const rememberMe = ref(false)
const registerVisible = ref(false)
const registerAllowed = ref(true)
const requireInviteCode = ref(false)
const loginFormRef = ref()
const registerFormRef = ref()

const loginForm = reactive({ username: '', password: '' })
const registerForm = reactive({
  username: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  inviteCode: ''
})

const loginRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    {
      pattern: /^[a-zA-Z][a-zA-Z0-9_]{2,31}$/,
      message: '3–32位，字母开头，仅字母/数字/下划线（不可中文或特殊符号）',
      trigger: ['blur', 'change']
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        if (v !== registerForm.password) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ],
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 6, message: '昵称最多6个字', trigger: 'blur' }
  ],
  inviteCode: [
    {
      validator: (_r, v, cb) => {
        if (requireInviteCode.value && (!v || !String(v).trim())) {
          cb(new Error('请输入邀请码'))
        } else cb()
      },
      trigger: 'blur'
    }
  ]
}

function persistSession(data) {
  const token = data?.token
  const user = data?.user
  if (!token || token.split('.').length !== 3) {
    throw new Error('登录返回的 token 非法')
  }
  // localStorage：多标签页共享登录态（sessionStorage 每开新标签都要重登）
  localStorage.setItem('token', token)
  localStorage.setItem('user', JSON.stringify(user || {}))
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
}

async function handleLogin() {
  if (loading.value) return
  const form = loginFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch {
    return
  }
  loading.value = true
  try {
    const data = await login({
      username: loginForm.username,
      password: loginForm.password
    })
    persistSession(data)
    if (rememberMe.value) {
      localStorage.setItem(REMEMBER_KEY, loginForm.username)
    } else {
      localStorage.removeItem(REMEMBER_KEY)
    }
    ElMessage.success('登录成功')
    router.push('/app/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}

function openRegister() {
  registerVisible.value = true
}

function resetRegister() {
  registerForm.username = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  registerForm.nickname = ''
  registerForm.inviteCode = ''
}

async function handleRegister() {
  const form = registerFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch {
    return
  }
  registering.value = true
  try {
    await register({ ...registerForm })
    const data = await login({
      username: registerForm.username,
      password: registerForm.password
    })
    persistSession(data)
    registerVisible.value = false
    ElMessage.success('注册成功')
    router.push('/app/dashboard')
  } catch (e) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    registering.value = false
  }
}

onMounted(async () => {
  const saved = localStorage.getItem(REMEMBER_KEY)
  if (saved) {
    loginForm.username = saved
    rememberMe.value = true
  }
  try {
    const status = await fetchRegisterStatus()
    registerAllowed.value = status?.allowed !== false
    requireInviteCode.value = status?.requireInviteCode === true
  } catch {
    registerAllowed.value = true
  }
})
</script>

<style scoped>
.login-page {
  width: 100%;
  height: 100%;
  display: flex;
  min-height: 100%;
}

.login-brand {
  width: 44%;
  min-width: 360px;
  background: linear-gradient(145deg, #4f46e5, #5b5ce2 45%, #7c3aed);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.brand-content {
  position: relative;
  z-index: 1;
  text-align: center;
  padding: 40px;
  color: #fff;
}

.brand-logo {
  margin-bottom: 16px;
  filter: drop-shadow(0 10px 18px rgba(0, 0, 0, 0.2));
}

.brand-name {
  font-size: 42px;
  font-weight: 800;
  letter-spacing: 4px;
  margin-bottom: 8px;
}

.brand-slogan {
  font-size: 14px;
  opacity: 0.9;
  letter-spacing: 2px;
  margin-bottom: 28px;
}

.brand-features {
  display: inline-flex;
  flex-direction: column;
  gap: 12px;
  text-align: left;
  padding: 18px 24px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.12);
  border: 1px solid rgba(255, 255, 255, 0.16);
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.85);
}

.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px 24px;
  background:
    radial-gradient(circle at 100% 0%, rgba(91, 92, 226, 0.07), transparent 36%),
    #fff;
}

.login-card {
  width: 100%;
  max-width: 400px;
}

.card-header {
  margin-bottom: 28px;
}

.card-header h2 {
  font-size: 30px;
  font-weight: 800;
  letter-spacing: -0.02em;
  margin-bottom: 8px;
}

.card-header p {
  color: var(--text-secondary);
}

.full-btn {
  width: 100%;
}

.options-row {
  margin-bottom: 8px;
}

.register-tip {
  margin-top: -6px;
  font-size: 12px;
  color: var(--text-muted);
  text-align: center;
}

.dialog-tip {
  margin: -4px 0 12px;
  color: var(--text-secondary);
  font-size: 13px;
}

@media (max-width: 900px) {
  .login-page {
    flex-direction: column;
    overflow: auto;
  }
  .login-brand {
    width: 100%;
    min-width: 0;
    min-height: 220px;
    padding: 28px 16px;
  }
  .brand-name {
    font-size: 32px;
  }
  .brand-features {
    display: none;
  }
  .login-form-area {
    padding: 28px 18px 40px;
  }
}
</style>
