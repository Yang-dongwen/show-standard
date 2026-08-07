import { request } from './http.js'

export function fetchStaffAccounts() {
  return request('/api/staff')
}

export function fetchStaffRoles() {
  return request('/api/staff/roles', { silent: true })
}

export function createStaffAccount(payload) {
  return request('/api/staff', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateStaffAccount(id, payload) {
  return request(`/api/staff/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function resetStaffPassword(id, newPassword) {
  return request(`/api/staff/${id}/reset-password`, {
    method: 'POST',
    body: JSON.stringify({ newPassword })
  })
}

export function toggleStaffStatus(id) {
  return request(`/api/staff/${id}/toggle-status`, {
    method: 'PATCH'
  })
}
