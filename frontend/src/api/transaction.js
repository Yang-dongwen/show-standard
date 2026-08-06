import { request } from './http.js'

export function fetchTransactions({ keyword = '', page = 1, size = 8 } = {}) {
  return request(
    `/api/transactions?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`
  )
}

export function createRecharge(payload) {
  return request('/api/transactions/recharge', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

export function createConsume(payload) {
  return request('/api/transactions/consume', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}
