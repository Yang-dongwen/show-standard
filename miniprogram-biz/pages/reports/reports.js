const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')
const { today, daysAgo } = require('../../utils/format')

Page({
  data: {
    startDate: '',
    endDate: '',
    summary: {},
    performance: []
  },
  onShow() {
    if (!requireLogin()) return
    this.setData({ startDate: daysAgo(7), endDate: today() })
    this.load()
  },
  async load() {
    const { startDate, endDate } = this.data
    try {
      const [summary, performance] = await Promise.all([
        request(`/api/reports/summary?startDate=${startDate}&endDate=${endDate}`),
        request(`/api/reports/employee-performance?startDate=${startDate}&endDate=${endDate}`)
      ])
      this.setData({ summary: summary || {}, performance: performance || [] })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  }
})
