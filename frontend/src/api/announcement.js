import { request } from './http.js'

export function fetchAnnouncements() {
  return request('/api/announcements')
}
