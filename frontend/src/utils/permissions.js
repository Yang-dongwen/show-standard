/** 角色权限工具（与后端 StaffRole 对齐） */

export const ROLE_LABELS = {
  owner: '店长',
  cashier: '收银员',
  staff: '店员',
  admin: '店长'
}

export function getSessionUser() {
  try {
    return JSON.parse(localStorage.getItem('user') || sessionStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
}

export function roleLabel(role) {
  return ROLE_LABELS[role] || ROLE_LABELS.owner
}

export function permissionSet(user) {
  const u = user || getSessionUser()
  if (Array.isArray(u.permissions) && u.permissions.length) {
    return new Set(u.permissions)
  }
  // 兼容旧会话 / 无 permissions 字段
  const role = u.role || 'owner'
  if (role === 'owner' || role === 'admin' || !role) {
    return new Set([
      'dashboard',
      'customers',
      'customers:write',
      'customers:verify',
      'transactions',
      'recharge',
      'consume',
      'reverse',
      'employees',
      'reports',
      'audit',
      'settings',
      'backup',
      'staff_accounts'
    ])
  }
  if (role === 'cashier') {
    return new Set([
      'dashboard',
      'customers',
      'customers:write',
      'transactions',
      'recharge',
      'consume',
      'reverse',
      'reports'
    ])
  }
  return new Set(['dashboard', 'customers', 'customers:write', 'transactions', 'consume'])
}

export function hasPermission(perm, user) {
  if (!perm) return true
  return permissionSet(user).has(perm)
}

export function hasAnyPermission(perms, user) {
  if (!perms || !perms.length) return true
  const set = permissionSet(user)
  return perms.some((p) => set.has(p))
}
