async function saasRequest(path, options = {}) {
  const token = sessionStorage.getItem('saasToken')
  let res
  try {
    res = await fetch(path, {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(options.headers || {})
      },
      ...options
    })
  } catch {
    throw new Error('无法连接服务器')
  }
  let data
  try {
    data = await res.json()
  } catch {
    throw new Error('响应解析失败')
  }
  if (!res.ok || data.success === false) {
    if (res.status === 401) {
      sessionStorage.removeItem('saasToken')
      sessionStorage.removeItem('saasUser')
      if (!window.location.hash.includes('/login')) {
        window.location.hash = '#/login'
      }
    }
    throw new Error(data.message || '请求失败')
  }
  return data.data
}

export function saasLogin(payload) {
  return saasRequest('/api/saas/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchDashboard() {
  return saasRequest('/api/saas/dashboard')
}

export function fetchTenants() {
  return saasRequest('/api/saas/tenants')
}

export function fetchTenantDetail(id) {
  return saasRequest(`/api/saas/tenants/${id}`)
}

export function suspendTenant(id) {
  return saasRequest(`/api/saas/tenants/${id}/suspend`, { method: 'POST' })
}

export function activateTenant(id) {
  return saasRequest(`/api/saas/tenants/${id}/activate`, { method: 'POST' })
}

export function updateTenantPlan(id, payload) {
  return saasRequest(`/api/saas/tenants/${id}/plan`, {
    method: 'PUT',
    body: JSON.stringify(payload || {})
  })
}

export function applyTenantPlan(id, planCode) {
  return saasRequest(`/api/saas/tenants/${id}/apply-plan/${planCode}`, { method: 'POST' })
}

export function updateTenantMeta(id, payload) {
  return saasRequest(`/api/saas/tenants/${id}/meta`, {
    method: 'PUT',
    body: JSON.stringify(payload || {})
  })
}

export function resetManagerPassword(id, newPassword) {
  return saasRequest(`/api/saas/tenants/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ newPassword })
  })
}

export function fetchInvites() {
  return saasRequest('/api/saas/invites')
}

export function createInvite(payload) {
  return saasRequest('/api/saas/invites', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  })
}

export function revokeInvite(id) {
  return saasRequest(`/api/saas/invites/${id}/revoke`, { method: 'POST' })
}

export function registerShop(payload) {
  return saasRequest('/api/saas/public/register-shop', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchAudit(limit = 100) {
  return saasRequest(`/api/saas/audit?limit=${limit}`)
}

export function fetchPlans() {
  return saasRequest('/api/saas/plans')
}

export function fetchAnnouncements() {
  return saasRequest('/api/saas/announcements')
}

export function createAnnouncement(payload) {
  return saasRequest('/api/saas/announcements', {
    method: 'POST',
    body: JSON.stringify(payload || {})
  })
}

export function revokeAnnouncement(id) {
  return saasRequest(`/api/saas/announcements/${id}/revoke`, { method: 'POST' })
}

export function renewTenant(id, payload) {
  return saasRequest(`/api/saas/tenants/${id}/renew`, {
    method: 'POST',
    body: JSON.stringify(payload || {})
  })
}

export function setWriteMode(id, writeMode) {
  return saasRequest(`/api/saas/tenants/${id}/write-mode`, {
    method: 'PUT',
    body: JSON.stringify({ writeMode })
  })
}

export function fetchBillings(limit = 100) {
  return saasRequest(`/api/saas/billings?limit=${limit}`)
}

export function fetchTenantBillings(id) {
  return saasRequest(`/api/saas/tenants/${id}/billings`)
}
