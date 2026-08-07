import { request } from './http.js'

export function fetchShop() {
  return request('/api/shop')
}

export function updateShop(payload) {
  return request('/api/shop', {
    method: 'PUT',
    body: JSON.stringify(payload)
  })
}
