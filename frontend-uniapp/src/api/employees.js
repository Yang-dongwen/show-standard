import { request } from '@/utils/request'

export function listEmployees(params = {}) {
  return request('/api/employees', { data: params })
}

export function employeeOptions() {
  return request('/api/employees/options')
}

export function createEmployee(data) {
  return request('/api/employees', { method: 'POST', data })
}

export function updateEmployee(id, data) {
  return request('/api/employees/' + id, { method: 'PUT', data })
}

export function patchEmployee(id, data) {
  return request('/api/employees/' + id, { method: 'PATCH', data })
}

export function toggleEmployeeStatus(id) {
  return request('/api/employees/' + id + '/toggle-status', { method: 'PATCH' })
}
