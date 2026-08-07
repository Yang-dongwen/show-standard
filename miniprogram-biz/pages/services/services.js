const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')

Page({
  data: { items: [], name: '', price: '' },
  onShow() {
    if (!requireLogin()) return
    this.load()
  },
  async load() {
    try {
      const data = await request('/api/config/services?page=1&size=50')
      this.setData({ items: data.items || data || [] })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  },
  onField(e) {
    this.setData({ [e.currentTarget.dataset.k]: e.detail.value })
  },
  async add() {
    const { name, price } = this.data
    if (!name) return
    try {
      await request('/api/config/services', {
        method: 'POST',
        data: { name, price: Number(price || 0) }
      })
      this.setData({ name: '', price: '' })
      this.load()
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  }
})
