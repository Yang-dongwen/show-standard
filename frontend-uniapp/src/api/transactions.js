import { request } from '@/utils/request'

export function listTransactions(params = {}) {
  return request('/api/transactions', { data: params })
}

export function recharge(data) {
  return request('/api/transactions/recharge', { method: 'POST', data })
}

export function consume(data) {
  return request('/api/transactions/consume', { method: 'POST', data })
}
