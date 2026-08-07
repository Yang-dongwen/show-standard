import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', redirect: '/login' },
    {
      path: '/login',
      name: 'Login',
      component: () => import('@/views/LoginView.vue')
    },
    {
      path: '/open-shop',
      name: 'OpenShop',
      component: () => import('@/views/OpenShopView.vue')
    },
    {
      path: '/home',
      component: () => import('@/layouts/SaasLayout.vue'),
      meta: { requiresAuth: true },
      redirect: '/home/dashboard',
      children: [
        {
          path: 'dashboard',
          name: 'Dashboard',
          component: () => import('@/views/dashboard/DashboardView.vue'),
          meta: { title: '驾驶舱', requiresAuth: true }
        },
        {
          path: 'tenants',
          name: 'Tenants',
          component: () => import('@/views/tenants/TenantsView.vue'),
          meta: { title: '租户门店', requiresAuth: true }
        },
        {
          path: 'invites',
          name: 'Invites',
          component: () => import('@/views/invites/InvitesView.vue'),
          meta: { title: '邀请码', requiresAuth: true }
        },
        {
          path: 'billings',
          name: 'Billings',
          component: () => import('@/views/billings/BillingsView.vue'),
          meta: { title: '续期账单', requiresAuth: true }
        },
        {
          path: 'plans',
          name: 'Plans',
          component: () => import('@/views/plans/PlansView.vue'),
          meta: { title: '套餐目录', requiresAuth: true }
        },
        {
          path: 'announcements',
          name: 'Announcements',
          component: () => import('@/views/announcements/AnnouncementsView.vue'),
          meta: { title: '公告下发', requiresAuth: true }
        },
        {
          path: 'audit',
          name: 'Audit',
          component: () => import('@/views/audit/AuditView.vue'),
          meta: { title: '操作审计', requiresAuth: true }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = sessionStorage.getItem('saasToken')
  const needAuth = to.matched.some((r) => r.meta.requiresAuth)
  if (needAuth && !token) {
    next('/login')
    return
  }
  if (to.path === '/login' && token) {
    next('/home/dashboard')
    return
  }
  next()
})

export default router
