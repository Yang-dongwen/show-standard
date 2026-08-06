import { request } from './http.js'

export function fetchEmployees({ keyword = '', page = 1, size = 8 } = {}) {
  return request(
    `/api/employees?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
  )
}

export function createEmployee(payload) {
  return request('/api/employees', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateEmployee(id, payload) {
  return request(`/api/employees/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function toggleEmployeeStatus(id) {
  return request(`/api/employees/${id}/toggle-status`, { method: 'PATCH' })
}
