import { request } from './http.js'

export function fetchCustomers({ keyword = '', page = 1, size = 8 } = {}) {
  return request(
    `/api/customers?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
  )
}

export function createCustomer(payload) {
  return request('/api/customers', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function updateCustomer(id, payload) {
  return request(`/api/customers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}

export function toggleCustomerStatus(id) {
  return request(`/api/customers/${id}/toggle-status`, { method: 'PATCH' })
}

export function fetchBalance(customerId) {
  return request(`/api/accounts/${customerId}/balance`)
}

/** 下拉远程搜索：只取一页活跃会员，列表已含 balance */
export async function searchCustomersForSelect(keyword = '', size = 30) {
  const data = await fetchCustomers({ keyword, page: 1, size })
  return (data.items || []).filter((c) => c.status === 'active')
}

