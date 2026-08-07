<template>
  <div class="main-layout" :class="{ collapsed: sidebarCollapsed }">
    <aside class="sidebar">
      <div class="brand-block">
        <div class="brand-mark">S</div>
        <div v-show="!sidebarCollapsed" class="brand-text">
          <div class="brand-name">Show</div>
          <div class="brand-sub">{{ shopTitle }}</div>
        </div>
      </div>

      <nav class="menu-wrap">
        <el-menu
          :default-active="activeMenu"
          :collapse="sidebarCollapsed"
          class="sidebar-menu"
          background-color="transparent"
          text-color="#4b5563"
          active-text-color="#5b5ce2"
          router
          :collapse-transition="false"
        >
          <el-menu-item v-for="item in navItems" :key="item.path" :index="item.path">
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>
              <span class="menu-label">{{ item.label }}</span>
            </template>
          </el-menu-item>
        </el-menu>
      </nav>

      <div class="sidebar-bottom">
        <div v-show="!sidebarCollapsed" class="user-mini">
          <el-avatar :size="32" :src="avatarUrl" />
          <div class="user-mini-info">
            <div class="user-mini-name">{{ displayName }}</div>
            <div class="user-mini-role">{{ currentRoleLabel }}</div>
          </div>
        </div>
        <button
          type="button"
          class="collapse-trigger"
          :title="sidebarCollapsed ? '展开菜单' : '收起菜单'"
          @click="sidebarCollapsed = !sidebarCollapsed"
        >
          <el-icon :size="16"><Fold v-if="!sidebarCollapsed" /><Expand v-else /></el-icon>
        </button>
      </div>
    </aside>

    <section class="main-panel">
      <header class="content-header">
        <div class="header-title-block">
          <div class="title-icon-wrap">
            <el-icon :size="18"><component :is="pageIcon" /></el-icon>
          </div>
          <div class="title-meta">
            <div class="title-row">
              <h1 class="page-title">{{ pageTitle }}</h1>
              <span class="title-divider" />
              <span class="date-chip">
                <el-icon :size="13"><Calendar /></el-icon>
                {{ currentDate }}
              </span>
            </div>
            <p class="page-desc">{{ pageSubtitle }}</p>
          </div>
        </div>
        <div class="header-actions">
          <el-tooltip content="刷新当前页" placement="bottom">
            <el-button
              class="icon-action-btn"
              :icon="Refresh"
              :loading="refreshing"
              @click="emitRefresh"
            />
          </el-tooltip>
          <el-dropdown trigger="click" @command="onUserCommand">
            <div class="user-chip">
              <el-avatar :size="30" :src="avatarUrl" />
              <span class="user-chip-name">{{ displayName }}</span>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="password">修改密码</el-dropdown-item>
                <el-dropdown-item divided command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content-body">
        <!-- 不缓存页面：切换菜单重新挂载并拉最新数据，避免收银/会员列表过期 -->
        <router-view v-slot="{ Component, route: r }">
          <component :is="Component" :key="r.fullPath" />
        </router-view>
      </main>
    </section>

    <el-dialog
      v-model="passwordVisible"
      title="修改密码"
      width="400px"
      align-center
      destroy-on-close
      @closed="resetPwdForm"
    >
      <el-form
        ref="pwdFormRef"
        :model="pwdForm"
        :rules="pwdRules"
        label-position="left"
        label-width="88px"
      >
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            class="field-xl"
            type="password"
            show-password
            placeholder="请输入旧密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            class="field-xl"
            type="password"
            show-password
            placeholder="至少6位"
          />
        </el-form-item>
        <el-form-item label="确认新密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            class="field-xl"
            type="password"
            show-password
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdLoading" @click="submitChangePassword">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, provide, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Refresh,
  ArrowDown,
  Fold,
  Expand,
  Calendar,
  DataBoard,
  User,
  Wallet,
  Avatar,
  TrendCharts,
  Document,
  Setting,
  QuestionFilled,
  UserFilled
} from '@element-plus/icons-vue'
import { changePassword } from '@/api/auth.js'
import { formatChineseDate } from '@/utils/format.js'
import { hasPermission, roleLabel } from '@/utils/permissions.js'
import avatarUrl from '@/assets/avatar.png'

const router = useRouter()
const route = useRoute()

const allNavItems = [
  { path: '/app/dashboard', label: '经营总览', icon: DataBoard, subtitle: '今日经营概况与快捷入口', permission: 'dashboard' },
  { path: '/app/customers', label: '会员管理', icon: User, subtitle: '会员档案、余额与校验码', permission: 'customers' },
  { path: '/app/transactions', label: '充值消费', icon: Wallet, subtitle: '收银入账、扣款与流水', permission: 'transactions' },
  { path: '/app/employees', label: '员工管理', icon: Avatar, subtitle: '在岗员工与状态维护', permission: 'employees' },
  { path: '/app/staff', label: '登录账号', icon: UserFilled, subtitle: '店员/收银登录账号与角色', permission: 'staff_accounts' },
  { path: '/app/reports', label: '报表分析', icon: TrendCharts, subtitle: '区间汇总与员工业绩', permission: 'reports' },
  { path: '/app/audit', label: '审计日志', icon: Document, subtitle: '关键操作可追溯记录', permission: 'audit' },
  { path: '/app/settings', label: '门店设置', icon: Setting, subtitle: '门店资料、服务与默认价格', permission: 'settings' },
  { path: '/app/help', label: '使用帮助', icon: QuestionFilled, subtitle: '使用说明与访问地址' }
]

const navItems = computed(() =>
  allNavItems.filter((item) => !item.permission || hasPermission(item.permission, user.value))
)

const shopTitle = computed(() => {
  try {
    const u = JSON.parse(sessionStorage.getItem('user') || '{}')
    return u.shopName || '门店会员'
  } catch {
    return '门店会员'
  }
})

const sidebarCollapsed = ref(false)
const refreshing = ref(false)
const passwordVisible = ref(false)
const pwdLoading = ref(false)
const pwdFormRef = ref()
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码至少6位', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_r, v, cb) => {
        if (v !== pwdForm.newPassword) cb(new Error('两次输入的新密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

const refreshHandlers = new Set()
provide('registerRefresh', (fn) => {
  refreshHandlers.add(fn)
  return () => refreshHandlers.delete(fn)
})

const user = computed(() => {
  try {
    return JSON.parse(sessionStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})
const displayName = computed(() => user.value.nickname || user.value.username || '店长')
const currentRoleLabel = computed(() => roleLabel(user.value.role || 'owner'))
const activeMenu = computed(() => route.path)
const currentNav = computed(
  () => navItems.value.find((n) => n.path === route.path) || navItems.value[0] || allNavItems[0]
)
const pageTitle = computed(() => route.meta.title || currentNav.value.label || '经营总览')
const pageIcon = computed(() => currentNav.value.icon || DataBoard)
const pageSubtitle = computed(() => currentNav.value.subtitle || '门店会员管理系统')
const currentDate = formatChineseDate()

async function emitRefresh() {
  refreshing.value = true
  const tasks = [...refreshHandlers].map((fn) => Promise.resolve(fn()))
  try {
    await Promise.all(tasks)
    ElMessage.success('已刷新')
  } catch {
    // handled
  } finally {
    refreshing.value = false
  }
}

function onUserCommand(cmd) {
  if (cmd === 'password') passwordVisible.value = true
  if (cmd === 'logout') handleLogout()
}

function resetPwdForm() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
}

async function submitChangePassword() {
  const form = pwdFormRef.value
  if (!form) return
  try {
    await form.validate()
  } catch {
    return
  }
  pwdLoading.value = true
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码修改成功，请重新登录')
    passwordVisible.value = false
    doLogout(false)
  } catch {
    // handled
  } finally {
    pwdLoading.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      type: 'warning',
      confirmButtonText: '退出',
      cancelButtonText: '取消'
    })
    doLogout(true)
  } catch {
    // cancelled
  }
}

function doLogout(showMsg) {
  sessionStorage.removeItem('token')
  sessionStorage.removeItem('user')
  if (showMsg) ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
/* 浅色侧栏：白底 + 左边强调条选中，字号与间距加大 */
.main-layout {
  width: 100%;
  height: 100%;
  display: flex;
  background: var(--bg-page);
}

.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: #ffffff;
  display: flex;
  flex-direction: column;
  color: var(--text-primary);
  transition: width 0.2s ease, min-width 0.2s ease;
  z-index: 10;
  border-right: 1px solid var(--border);
  box-shadow: 1px 0 0 rgba(15, 23, 42, 0.02);
}

.main-layout.collapsed .sidebar {
  width: var(--sidebar-collapsed);
  min-width: var(--sidebar-collapsed);
}

.brand-block {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--border-soft);
  flex-shrink: 0;
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  display: grid;
  place-items: center;
  font-weight: 800;
  font-size: 15px;
  color: #fff;
  background: var(--accent);
  flex-shrink: 0;
}

.brand-name {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.2;
}

.brand-sub {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 2px;
}

.menu-wrap {
  flex: 1;
  overflow: auto;
  padding: 12px 0;
}

.sidebar-menu {
  border-right: none !important;
  background: transparent !important;
  width: 100% !important;
}

.sidebar-menu :deep(.el-menu-item) {
  height: 52px;
  line-height: 52px;
  margin: 4px 12px;
  border-radius: 10px;
  padding-left: 16px !important;
  color: #4b5563 !important;
  transition: background 0.15s ease, color 0.15s ease;
}

.sidebar-menu :deep(.el-menu-item .el-icon) {
  font-size: 20px;
  margin-right: 12px;
  width: 22px;
  color: inherit;
}

.sidebar-menu :deep(.el-menu-item .menu-label) {
  font-size: 15px;
  font-weight: 500;
  letter-spacing: 0.02em;
}

/* 选中：浅紫底 + 左侧强调条 */
.sidebar-menu :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9) !important;
  color: var(--accent) !important;
  font-weight: 600;
  position: relative;
  box-shadow: none !important;
}

.sidebar-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 12px;
  bottom: 12px;
  width: 3px;
  border-radius: 0 3px 3px 0;
  background: var(--accent);
}

.sidebar-menu :deep(.el-menu-item:not(.is-active):hover) {
  background: #f3f4f6 !important;
  color: #111827 !important;
}

/* 折叠态 */
.sidebar-menu.el-menu--collapse {
  width: 100% !important;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item) {
  margin: 4px 8px;
  padding: 0 !important;
  display: flex;
  justify-content: center;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item .el-icon) {
  margin-right: 0;
}

.sidebar-menu.el-menu--collapse :deep(.el-menu-item.is-active::before) {
  top: 10px;
  bottom: 10px;
}

.sidebar-bottom {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 14px 14px;
  border-top: 1px solid var(--border-soft);
  flex-shrink: 0;
  background: #fafbfc;
}

.user-mini {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-mini-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-mini-role {
  font-size: 12px;
  color: var(--text-muted);
}

.collapse-trigger {
  width: 34px;
  height: 34px;
  border: 1px solid var(--border);
  border-radius: 8px;
  background: #fff;
  color: var(--text-secondary);
  display: grid;
  place-items: center;
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s ease, color 0.15s ease, border-color 0.15s ease;
}

.collapse-trigger:hover {
  background: var(--el-color-primary-light-9);
  color: var(--accent);
  border-color: var(--el-color-primary-light-5);
}

.main-layout.collapsed .sidebar-bottom {
  justify-content: center;
  background: #fff;
}

.main-panel {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.content-header {
  min-height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 10px 20px;
  background: #fff;
  border-bottom: 1px solid var(--border-soft);
}

.header-title-block {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.title-icon-wrap {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
  color: var(--accent);
  background: linear-gradient(145deg, var(--el-color-primary-light-9), #fff);
  border: 1px solid var(--el-color-primary-light-8);
  box-shadow: 0 2px 8px rgba(91, 92, 226, 0.08);
}

.title-meta {
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.page-title {
  margin: 0;
  font-size: 17px;
  font-weight: 700;
  color: var(--text-primary);
  line-height: 1.25;
  letter-spacing: 0.01em;
}

.title-divider {
  width: 1px;
  height: 12px;
  background: #e5e7eb;
  flex-shrink: 0;
}

.date-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 12px;
  color: var(--text-secondary);
  background: #f3f4f6;
  line-height: 1.4;
}

.page-desc {
  margin-top: 3px;
  font-size: 12px;
  color: var(--text-muted);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 420px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

@media (max-width: 720px) {
  .title-divider,
  .date-chip {
    display: none;
  }
  .page-desc {
    max-width: 180px;
  }
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 10px 3px 3px;
  border-radius: 20px;
  background: #f8fafc;
  border: 1px solid var(--border-soft);
  cursor: pointer;
  color: var(--text-primary);
  transition: background 0.15s ease;
}

.user-chip:hover {
  background: #f1f5f9;
}

.user-chip-name {
  font-size: 13px;
  font-weight: 500;
  max-width: 88px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.content-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  padding: 12px 16px 14px;
}

@media (max-width: 900px) {
  .sidebar {
    width: var(--sidebar-collapsed);
    min-width: var(--sidebar-collapsed);
  }
  .brand-text,
  .user-mini {
    display: none !important;
  }
  .user-chip-name {
    display: none;
  }
}
</style>
