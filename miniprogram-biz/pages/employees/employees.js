const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')

Page({
  data: { items: [], name: '' },
  onShow() {
    if (!requireLogin()) return
    this.load()
  },
  async load() {
    try {
      const data = await request('/api/employees?page=1&size=50')
      this.setData({ items: data.items || [] })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  },
  onName(e) {
    this.setData({ name: e.detail.value })
  },
  async add() {
    if (!this.data.name) return
    try {
      await request('/api/employees', { method: 'POST', data: { name: this.data.name } })
      this.setData({ name: '' })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  }
})
