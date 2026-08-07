import { request } from '@/utils/request'

export function listCustomers(params = {}) {
  return request('/api/customers', { data: params })
}

export function createCustomer(data) {
  return request('/api/customers', { method: 'POST', data })
}

export function updateCustomer(id, data) {
  return request('/api/customers/' + id, { method: 'PUT', data })
}

export function toggleCustomerStatus(id) {
  return request('/api/customers/' + id + '/toggle-status', { method: 'PATCH' })
}
