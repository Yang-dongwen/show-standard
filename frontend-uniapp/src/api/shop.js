import { request } from '@/utils/request'

export function getShop() {
  return request('/api/shop')
}

export function updateShop(data) {
  return request('/api/shop', { method: 'PUT', data })
}
