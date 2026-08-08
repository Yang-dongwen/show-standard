import { createRouter, createWebHashHistory } from 'vue-router'
import { hasPermission } from '@/utils/permissions.js'
import { fetchInstallStatus } from '@/api/install.js'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/setup',
    name: 'Setup',
    component: () => import('@/views/setup/SetupView.vue'),
    meta: { public: true, setup: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { public: true }
  },
  {
    path: '/dashboard',
    redirect: '/app/dashboard'
  },
  {
    path: '/app',
    component: () => import('@/layouts/MainLayout.vue'),
    meta: { requiresAuth: true },
    redirect: '/app/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '经营总览', permission: 'dashboard' }
      },
      {
        path: 'customers',
        name: 'Customers',
        component: () => import('@/views/customers/CustomerView.vue'),
        meta: { title: '会员管理', permission: 'customers' }
      },
      {
        path: 'transactions',
        name: 'Transactions',
        component: () => import('@/views/transactions/TransactionView.vue'),
        meta: { title: '充值消费', permission: 'transactions' }
      },
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employees/EmployeeView.vue'),
        meta: { title: '员工管理', permission: 'employees' }
      },
      {
        path: 'staff',
        name: 'StaffAccounts',
        component: () => import('@/views/staff/StaffView.vue'),
        meta: { title: '登录账号', permission: 'staff_accounts' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/reports/ReportView.vue'),
        meta: { title: '报表分析', permission: 'reports' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/AuditView.vue'),
        meta: { title: '审计日志', permission: 'audit' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '门店设置', permission: 'settings' }
      },
      {
        path: 'help',
        name: 'Help',
        component: () => import('@/views/help/HelpView.vue'),
        meta: { title: '使用帮助' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

/** @type {boolean|null} */
let installCache = null
let installCacheAt = 0

async function resolveNeedsSetup() {
  if (sessionStorage.getItem('install.done') === '1') {
    return false
  }
  const now = Date.now()
  if (installCache !== null && now - installCacheAt < 3000) {
    return installCache
  }
  try {
    const st = await fetchInstallStatus()
    // 云端 SaaS 进程：后端 cloudServer=true / needsSetup=false，永不进安装向导
    if (st && (st.cloudServer || (st.completed && !st.needsSetup))) {
      sessionStorage.setItem('install.done', '1')
      if (st.edition) {
        sessionStorage.setItem('install.edition', st.edition)
      }
      installCache = false
      installCacheAt = now
      return false
    }
    installCache = !!(st && st.needsSetup)
    installCacheAt = now
    return installCache
  } catch {
    // 后端未就绪时不拦，避免白屏
    return false
  }
}

router.beforeEach(async (to, from, next) => {
  if (to.name !== 'Setup') {
    const needs = await resolveNeedsSetup()
    if (needs) {
      next({ name: 'Setup', replace: true })
      return
    }
  } else {
    // 已完成安装则离开向导
    try {
      const st = await fetchInstallStatus()
      if (st && st.completed && !st.needsSetup && sessionStorage.getItem('install.force') !== '1') {
        next({ path: '/login', replace: true })
        return
      }
    } catch {
      // stay
    }
  }

  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (to.meta.requiresAuth || to.matched.some((r) => r.meta.requiresAuth)) {
    if (!token) {
      next('/login')
      return
    }
    const need = to.meta.permission || to.matched.map((r) => r.meta.permission).find(Boolean)
    if (need && !hasPermission(need)) {
      next('/app/dashboard')
      return
    }
  }
  if (to.path === '/login' && token) {
    next('/app/dashboard')
    return
  }
  next()
})

export default router
