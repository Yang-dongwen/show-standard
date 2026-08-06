import { createRouter, createWebHashHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/login'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/LoginView.vue')
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
        meta: { title: '经营总览' }
      },
      {
        path: 'customers',
        name: 'Customers',
        component: () => import('@/views/customers/CustomerView.vue'),
        meta: { title: '会员管理' }
      },
      {
        path: 'transactions',
        name: 'Transactions',
        component: () => import('@/views/transactions/TransactionView.vue'),
        meta: { title: '充值消费' }
      },
      {
        path: 'employees',
        name: 'Employees',
        component: () => import('@/views/employees/EmployeeView.vue'),
        meta: { title: '员工管理' }
      },
      {
        path: 'reports',
        name: 'Reports',
        component: () => import('@/views/reports/ReportView.vue'),
        meta: { title: '报表分析' }
      },
      {
        path: 'audit',
        name: 'Audit',
        component: () => import('@/views/audit/AuditView.vue'),
        meta: { title: '审计日志' }
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('@/views/settings/SettingsView.vue'),
        meta: { title: '服务项目' }
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

router.beforeEach((to, from, next) => {
  const token = sessionStorage.getItem('token')
  if (to.meta.requiresAuth || to.matched.some((r) => r.meta.requiresAuth)) {
    if (!token) {
      next('/login')
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
