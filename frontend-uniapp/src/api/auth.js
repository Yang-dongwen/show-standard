import { request } from '@/utils/request'

export function login(data) {
  return request('/api/auth/login', { method: 'POST', data })
}

export function wxLogin(data) {
  return request('/api/auth/wx-login', { method: 'POST', data })
}

export function wxBind(data) {
  return request('/api/auth/wx-bind', { method: 'POST', data })
}

export function wxBindStatus() {
  return request('/api/auth/wx-bind-status')
}

export function wxUnbind() {
  return request('/api/auth/wx-unbind', { method: 'POST' })
}

export function me() {
  return request('/api/auth/me')
}

export function logout() {
  return request('/api/auth/logout', { method: 'POST', silent: true })
}

export function changePassword(data) {
  return request('/api/auth/change-password', { method: 'POST', data })
}
