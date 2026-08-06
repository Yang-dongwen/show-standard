import { request } from './http.js'

export function fetchServices() {
  return request('/api/config/services')
}

export function createService(payload) {
  return request('/api/config/services', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateService(id, payload) {
  return request(`/api/config/services/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function toggleServiceStatus(id) {
  return request(`/api/config/services/${id}/toggle-status`, { method: 'PATCH' })
}
