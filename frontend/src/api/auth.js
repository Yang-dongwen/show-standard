import { request } from './http.js'

export function login(payload) {
  return request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
    silent: true
  })
}

export function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
    silent: true
  })
}

export function changePassword(payload) {
  return request('/api/auth/change-password', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function fetchRegisterStatus() {
  return request('/api/auth/register-status', { silent: true })
}
