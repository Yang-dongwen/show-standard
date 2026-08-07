/**
 * 可选：根据 user.permissions 控制菜单/写入口显隐。
 * V1 默认可全显，失败靠后端 403。
 */

export function hasPermission(user, code) {
  if (!user) return false
  const perms = user.permissions || user.permissionList || []
  if (!Array.isArray(perms) || perms.length === 0) {
    // 无权限列表时默认放行（与 MP V1 一致，由后端兜底）
    return true
  }
  if (perms.includes('*') || perms.includes('admin')) return true
  return perms.includes(code)
}

export function canWrite(user) {
  // 租户只读由后端 403 提示；此处仅做粗粒度
  if (!user) return false
  if (user.readonly === true || user.readOnly === true) return false
  return true
}

export default { hasPermission, canWrite }
