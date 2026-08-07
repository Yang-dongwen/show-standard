import { request } from './http.js'

export function fetchInstallStatus() {
  return request('/api/install/status', { silent: true })
}

export function testMysql(payload) {
  return request('/api/install/test-mysql', {
    method: 'POST',
    body: JSON.stringify(payload),
    silent: true
  })
}

export function completeInstall(payload) {
  return request('/api/install/complete', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
