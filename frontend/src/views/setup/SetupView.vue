<template>
  <div class="setup-page">
    <div class="setup-shell">
      <header class="setup-header">
        <div class="logo-mark">S</div>
        <div>
          <h1>Show 安装向导</h1>
          <p>请选择产品安装方式。选定后写入本机配置，一般仅首次需要。</p>
        </div>
      </header>

      <div v-if="loading" class="loading-wrap" v-loading="true" element-loading-text="检查安装状态…" />

      <template v-else>
        <el-steps :active="step" finish-status="success" align-center class="steps">
          <el-step title="选择版本" />
          <el-step title="连接配置" />
          <el-step title="完成" />
        </el-steps>

        <!-- Step 0: SKU -->
        <div v-show="step === 0" class="sku-grid">
          <button
            type="button"
            class="sku-card"
            :class="{ active: edition === 'local' }"
            @click="edition = 'local'"
          >
            <div class="sku-badge buyout">一次性买断</div>
            <h2>本地买断版</h2>
            <p class="sku-lead">本机安装，数据只存在本机，可离线使用</p>
            <ul>
              <li>数据库：本机 SQLite（无需安装 MySQL）</li>
              <li>门店会员、充值消费、报表、店员角色</li>
              <li>不支持微信小程序</li>
              <li>不包含 SaaS 运营台</li>
            </ul>
          </button>

          <button
            type="button"
            class="sku-card"
            :class="{ active: edition === 'saas' }"
            @click="edition = 'saas'"
          >
            <div class="sku-badge sub">订阅 · 云端</div>
            <h2>SaaS 云版</h2>
            <p class="sku-lead">连接 MySQL，支持多店开通、运营台与小程序</p>
            <ul>
              <li>数据库：云端 / 本机 MySQL（必填）</li>
              <li>SaaS 运营台、邀请开店、到期管控</li>
              <li>可启用商家微信小程序</li>
              <li>需能访问 MySQL 实例</li>
            </ul>
          </button>
        </div>

        <div v-show="step === 0" class="actions">
          <el-button type="primary" size="large" :disabled="!edition" @click="nextFromSku">
            下一步
          </el-button>
        </div>

        <!-- Step 1: config -->
        <div v-show="step === 1" class="config-panel">
          <template v-if="edition === 'local'">
            <el-result icon="success" title="本地买断版" sub-title="将使用本机 SQLite 存储，无需额外数据库。">
              <template #extra>
                <p class="hint">
                  数据目录：用户主目录下的 <code>.show</code> 文件夹（请定期备份）。
                </p>
              </template>
            </el-result>
          </template>

          <template v-else>
            <h3 class="panel-title">MySQL 连接（SaaS 云版必填）</h3>
            <p class="hint">开发可用本机或 Docker MySQL；生产填写云数据库地址。</p>
            <el-form label-position="left" label-width="100px" class="mysql-form">
              <el-form-item label="主机">
                <el-input v-model="mysql.host" placeholder="127.0.0.1" />
              </el-form-item>
              <el-form-item label="端口">
                <el-input-number v-model="mysql.port" :min="1" :max="65535" controls-position="right" />
              </el-form-item>
              <el-form-item label="数据库">
                <el-input v-model="mysql.database" placeholder="show" />
              </el-form-item>
              <el-form-item label="用户名">
                <el-input v-model="mysql.username" placeholder="show" />
              </el-form-item>
              <el-form-item label="密码">
                <el-input v-model="mysql.password" type="password" show-password placeholder="密码" />
              </el-form-item>
              <el-form-item label="小程序">
                <el-switch v-model="mysql.enableMiniProgram" active-text="启用商家小程序能力" />
              </el-form-item>
              <el-form-item label=" ">
                <el-button :loading="testing" @click="onTestMysql">测试连接</el-button>
                <span v-if="testMsg" class="test-msg" :class="{ ok: testOk }">{{ testMsg }}</span>
              </el-form-item>
            </el-form>
          </template>

          <div class="actions">
            <el-button size="large" @click="step = 0">上一步</el-button>
            <el-button type="primary" size="large" :loading="saving" @click="onComplete">
              {{ edition === 'local' ? '完成安装' : '保存并完成' }}
            </el-button>
          </div>
        </div>

        <!-- Step 2: done -->
        <div v-show="step === 2" class="done-panel">
          <el-result
            :icon="result.restartRequired ? 'warning' : 'success'"
            :title="result.editionLabel || '安装完成'"
            :sub-title="result.message"
          >
            <template #extra>
              <div class="done-actions">
                <el-button v-if="result.restartRequired" type="warning" size="large" @click="onRestartHint">
                  我已了解，将重启应用
                </el-button>
                <el-button type="primary" size="large" @click="goLogin">
                  进入登录
                </el-button>
              </div>
              <p v-if="result.restartRequired" class="hint warn">
                SaaS 配置已写入本机。请关闭当前程序后重新启动，才会切换到 MySQL 与云能力。
              </p>
            </template>
          </el-result>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { completeInstall, fetchInstallStatus, testMysql } from '@/api/install.js'

const router = useRouter()
const loading = ref(true)
const step = ref(0)
const edition = ref('')
const testing = ref(false)
const saving = ref(false)
const testOk = ref(false)
const testMsg = ref('')
const mysql = reactive({
  host: '127.0.0.1',
  port: 3306,
  database: 'show',
  username: 'show',
  password: 'show',
  enableMiniProgram: true
})
const result = reactive({
  editionLabel: '',
  message: '',
  restartRequired: false
})

function nextFromSku() {
  if (!edition.value) return
  step.value = 1
  testMsg.value = ''
  testOk.value = false
}

function payload() {
  return {
    edition: edition.value,
    mysqlHost: mysql.host,
    mysqlPort: mysql.port,
    mysqlDatabase: mysql.database,
    mysqlUsername: mysql.username,
    mysqlPassword: mysql.password,
    enableMiniProgram: mysql.enableMiniProgram
  }
}

async function onTestMysql() {
  testing.value = true
  testMsg.value = ''
  try {
    const data = await testMysql(payload())
    testOk.value = true
    testMsg.value = data.message || '连接成功'
    ElMessage.success('MySQL 连接成功')
  } catch (e) {
    testOk.value = false
    testMsg.value = e?.message || '连接失败'
  } finally {
    testing.value = false
  }
}

async function onComplete() {
  if (edition.value === 'saas' && !testOk.value) {
    try {
      await ElMessageBox.confirm(
        '尚未确认 MySQL 连接成功，仍要保存吗？保存后若无法连接，重启将启动失败。',
        '提示',
        { type: 'warning', confirmButtonText: '仍要保存', cancelButtonText: '去测试' }
      )
    } catch {
      return
    }
  }
  saving.value = true
  try {
    const data = await completeInstall(payload())
    result.editionLabel = data.editionLabel || ''
    result.message = data.message || '安装完成'
    result.restartRequired = !!data.restartRequired
    sessionStorage.setItem('install.done', '1')
    sessionStorage.setItem('install.edition', data.edition || edition.value)
    step.value = 2
    ElMessage.success('安装配置已保存')
  } catch {
    // http layer
  } finally {
    saving.value = false
  }
}

function goLogin() {
  router.replace('/login')
}

async function onRestartHint() {
  await ElMessageBox.alert(
    '请完全退出 Show（托盘图标选择退出），再重新打开。重启后将按 SaaS 云版连接 MySQL。',
    '需要重启',
    { confirmButtonText: '知道了' }
  )
}

onMounted(async () => {
  loading.value = true
  try {
    const st = await fetchInstallStatus()
    if (st && st.completed && !st.needsSetup) {
      sessionStorage.setItem('install.done', '1')
      router.replace('/login')
      return
    }
  } catch {
    // 首次无后端时仍展示向导
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.setup-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32px 16px;
  background: linear-gradient(145deg, #f4f6fb 0%, #e8eef9 45%, #f8f5ff 100%);
}
.setup-shell {
  width: min(920px, 100%);
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.08);
  padding: 28px 28px 32px;
}
.setup-header {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-bottom: 20px;
}
.logo-mark {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  background: linear-gradient(135deg, #5b5ce2, #7c3aed);
  color: #fff;
  font-weight: 700;
  font-size: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.setup-header h1 {
  margin: 0;
  font-size: 22px;
  color: #0f172a;
}
.setup-header p {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 13px;
}
.steps {
  margin: 8px 0 24px;
}
.sku-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
@media (max-width: 720px) {
  .sku-grid {
    grid-template-columns: 1fr;
  }
}
.sku-card {
  text-align: left;
  border: 2px solid #e2e8f0;
  border-radius: 14px;
  padding: 18px 18px 14px;
  background: #fafbff;
  cursor: pointer;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}
.sku-card:hover {
  border-color: #a5b4fc;
}
.sku-card.active {
  border-color: #5b5ce2;
  background: #f5f3ff;
  box-shadow: 0 0 0 3px rgba(91, 92, 226, 0.12);
}
.sku-badge {
  display: inline-block;
  font-size: 12px;
  padding: 2px 10px;
  border-radius: 999px;
  margin-bottom: 8px;
  font-weight: 600;
}
.sku-badge.buyout {
  background: #dcfce7;
  color: #166534;
}
.sku-badge.sub {
  background: #ede9fe;
  color: #5b21b6;
}
.sku-card h2 {
  margin: 0 0 6px;
  font-size: 18px;
  color: #0f172a;
}
.sku-lead {
  margin: 0 0 10px;
  color: #64748b;
  font-size: 13px;
}
.sku-card ul {
  margin: 0;
  padding-left: 18px;
  color: #334155;
  font-size: 13px;
  line-height: 1.7;
}
.actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 22px;
}
.config-panel .panel-title {
  margin: 0 0 6px;
  font-size: 16px;
}
.hint {
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}
.hint.warn {
  color: #b45309;
  margin-top: 12px;
}
.mysql-form {
  max-width: 480px;
  margin-top: 12px;
}
.test-msg {
  margin-left: 12px;
  font-size: 13px;
  color: #dc2626;
}
.test-msg.ok {
  color: #16a34a;
}
.done-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
  flex-wrap: wrap;
}
.loading-wrap {
  min-height: 200px;
}
code {
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
}
</style>
