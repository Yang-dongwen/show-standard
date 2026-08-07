import { request } from '@/utils/request'

export function listServices(params = {}) {
  return request('/api/config/services', { data: params })
}

export function serviceOptions() {
  return request('/api/config/services/options')
}

export function createService(data) {
  return request('/api/config/services', { method: 'POST', data })
}

export function updateService(id, data) {
  return request('/api/config/services/' + id, { method: 'PUT', data })
}

export function patchService(id, data) {
  return request('/api/config/services/' + id, { method: 'PATCH', data })
}

export function toggleServiceStatus(id) {
  return request('/api/config/services/' + id + '/toggle-status', { method: 'PATCH' })
}
