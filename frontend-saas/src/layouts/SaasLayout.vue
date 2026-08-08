<template>
  <div class="saas-shell">
    <aside class="sidebar">
      <div class="brand">
        <div class="brand-mark">S</div>
        <div class="brand-meta">
          <div class="brand-name">Show SaaS</div>
          <div class="brand-sub">运营中台</div>
        </div>
      </div>

      <nav class="nav">
        <div class="nav-label">运营</div>
        <router-link
          v-for="item in mainNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
          <span v-if="item.badge != null && item.badge > 0" class="badge">{{ item.badge }}</span>
        </router-link>

        <div class="nav-label">商业</div>
        <router-link
          v-for="item in bizNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>

        <div class="nav-label">系统</div>
        <router-link
          v-for="item in sysNav"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon :size="18"><component :is="item.icon" /></el-icon>
          <span>{{ item.label }}</span>
        </router-link>
      </nav>

      <div class="sidebar-foot">
        <div class="foot-card">
          <div class="foot-title">商家开店</div>
          <div class="foot-desc">邀请码开通门店账号</div>
          <el-button size="small" class="foot-btn" @click="$router.push('/open-shop')">
            开店入口
          </el-button>
        </div>
      </div>
    </aside>

    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <div class="crumb">
            <span class="crumb-muted">运营中台</span>
            <span class="crumb-sep">/</span>
            <span class="crumb-cur">{{ pageTitle }}</span>
          </div>
        </div>
        <div class="topbar-right">
          <div class="chip">
            <span class="chip-dot" />
            平台在线
          </div>
          <el-dropdown trigger="click" @command="onCmd">
            <div class="user-chip">
              <div class="avatar">{{ avatarLetter }}</div>
              <div class="user-meta">
                <div class="name">{{ displayName }}</div>
                <div class="role">平台运营</div>
              </div>
              <el-icon :size="12"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content">
        <router-view />
      </main>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowDown,
  DataBoard,
  OfficeBuilding,
  Ticket,
  Wallet,
  Goods,
  Bell,
  Document
} from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()

const mainNav = [
  { path: '/home/dashboard', label: '驾驶舱', icon: DataBoard },
  { path: '/home/tenants', label: '租户门店', icon: OfficeBuilding },
  { path: '/home/invites', label: '邀请码', icon: Ticket }
]

const bizNav = [
  { path: '/home/billings', label: '续期账单', icon: Wallet },
  { path: '/home/plans', label: '套餐目录', icon: Goods },
  { path: '/home/announcements', label: '公告下发', icon: Bell }
]

const sysNav = [
  { path: '/home/audit', label: '操作审计', icon: Document }
]

const pageTitle = computed(() => route.meta?.title || '驾驶舱')

const displayName = computed(() => {
  try {
    const u = JSON.parse(localStorage.getItem('saasUser') || sessionStorage.getItem('saasUser') || '{}')
    return u.nickname || u.username || '运营'
  } catch {
    return '运营'
  }
})

const avatarLetter = computed(() => {
  const n = displayName.value || 'S'
  return String(n).slice(0, 1).toUpperCase()
})

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function onCmd(cmd) {
  if (cmd === 'logout') {
    localStorage.removeItem('saasToken')
    localStorage.removeItem('saasUser')
    sessionStorage.removeItem('saasToken')
    sessionStorage.removeItem('saasUser')
    router.push('/login')
  }
}
</script>

<style scoped>
.saas-shell {
  display: flex;
  min-height: 100vh;
  background: var(--saas-bg);
}

.sidebar {
  width: var(--saas-sidebar-w);
  min-width: var(--saas-sidebar-w);
  background:
    radial-gradient(ellipse 80% 50% at 20% -10%, rgba(99, 102, 241, 0.35), transparent 55%),
    radial-gradient(ellipse 60% 40% at 100% 100%, rgba(139, 92, 246, 0.18), transparent 50%),
    linear-gradient(180deg, #0b1220 0%, #0f172a 55%, #111827 100%);
  color: var(--saas-sidebar-text);
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--saas-sidebar-border);
  position: sticky;
  top: 0;
  height: 100vh;
  z-index: 20;
}

.brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 22px 20px 18px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6 60%, #a78bfa);
  color: #fff;
  font-weight: 800;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8px 20px rgba(99, 102, 241, 0.35);
}

.brand-name {
  font-size: 15px;
  font-weight: 700;
  color: #f8fafc;
  letter-spacing: -0.01em;
}

.brand-sub {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.nav {
  flex: 1;
  padding: 8px 12px;
  overflow-y: auto;
}

.nav-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #475569;
  padding: 14px 12px 8px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: 10px;
  color: #94a3b8;
  text-decoration: none;
  font-size: 13.5px;
  font-weight: 500;
  margin-bottom: 2px;
  transition: background 0.15s ease, color 0.15s ease;
  position: relative;
}

.nav-item:hover {
  background: var(--saas-sidebar-hover);
  color: #e2e8f0;
}

.nav-item.active {
  background: var(--saas-sidebar-active);
  color: #eef2ff;
  box-shadow: inset 3px 0 0 #818cf8;
}

.nav-item .badge {
  margin-left: auto;
  background: #f43f5e;
  color: #fff;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  font-weight: 600;
}

.sidebar-foot {
  padding: 12px 14px 18px;
}

.foot-card {
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  padding: 14px;
}

.foot-title {
  font-size: 13px;
  font-weight: 600;
  color: #e2e8f0;
}

.foot-desc {
  font-size: 11px;
  color: #64748b;
  margin: 4px 0 10px;
  line-height: 1.4;
}

.foot-btn {
  width: 100%;
  --el-button-bg-color: rgba(99, 102, 241, 0.2);
  --el-button-border-color: rgba(99, 102, 241, 0.35);
  --el-button-text-color: #c7d2fe;
  --el-button-hover-bg-color: rgba(99, 102, 241, 0.35);
  --el-button-hover-border-color: rgba(99, 102, 241, 0.5);
  --el-button-hover-text-color: #eef2ff;
}

.main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.topbar {
  height: var(--saas-header-h);
  min-height: var(--saas-header-h);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28px;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--saas-border);
  position: sticky;
  top: 0;
  z-index: 10;
}

.crumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}

.crumb-muted { color: var(--saas-text-3); }
.crumb-sep { color: #cbd5e1; }
.crumb-cur { color: var(--saas-text); font-weight: 600; }

.topbar-right {
  display: flex;
  align-items: center;
  gap: 14px;
}

.chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--saas-text-2);
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  color: #047857;
  padding: 4px 10px;
  border-radius: 999px;
  font-weight: 500;
}

.chip-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.2);
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px 8px 4px 4px;
  border-radius: 999px;
  transition: background 0.15s;
}

.user-chip:hover {
  background: #f1f5f9;
}

.avatar {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-weight: 700;
  font-size: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.user-meta .name {
  font-size: 13px;
  font-weight: 600;
  color: var(--saas-text);
  line-height: 1.2;
}

.user-meta .role {
  font-size: 11px;
  color: var(--saas-text-3);
}

.content {
  flex: 1;
  padding: 24px 28px 40px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
}

@media (max-width: 900px) {
  .sidebar {
    display: none;
  }
  .content {
    padding: 16px;
  }
  .topbar {
    padding: 0 16px;
  }
}
</style>
