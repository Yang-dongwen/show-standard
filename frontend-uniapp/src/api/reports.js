import { request } from '@/utils/request'

export function dashboard() {
  return request('/api/reports/dashboard')
}

export function summary(params = {}) {
  return request('/api/reports/summary', { data: params })
}

export function employeePerformance(params = {}) {
  return request('/api/reports/employee-performance', { data: params })
}

export function serviceBreakdown(params = {}) {
  return request('/api/reports/service-breakdown', { data: params })
}
