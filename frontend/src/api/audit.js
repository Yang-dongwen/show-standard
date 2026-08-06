import { request } from './http.js'

export function fetchAuditLogs({ keyword = '' } = {}) {
  return request(`/api/audit/logs?keyword=${encodeURIComponent(keyword)}`)
}
