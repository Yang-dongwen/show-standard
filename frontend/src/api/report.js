import { request } from './http.js'

export function fetchDashboardSummary() {
  return request('/api/reports/dashboard')
}

export function fetchReportSummary({ startDate, endDate }) {
  return request(`/api/reports/summary?startDate=${startDate}&endDate=${endDate}`)
}

export function fetchEmployeePerformance({ startDate, endDate }) {
  return request(`/api/reports/employee-performance?startDate=${startDate}&endDate=${endDate}`)
}

export function fetchServiceBreakdown({ startDate, endDate }) {
  return request(`/api/reports/service-breakdown?startDate=${startDate}&endDate=${endDate}`)
}
