import { request } from './http.js'

export function fetchAccessInfo() {
  return request('/api/system/access-info')
}
