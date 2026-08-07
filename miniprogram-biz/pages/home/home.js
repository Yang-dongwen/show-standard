const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')
const { money } = require('../../utils/format')

Page({
  data: {
    summary: {},
    shopName: '',
    money
  },
  onShow() {
    if (!requireLogin()) return
    this.load()
  },
  async load() {
    wx.showNavigationBarLoading()
    try {
      const summary = await request('/api/reports/dashboard')
      const user = wx.getStorageSync('user') || {}
      this.setData({
        summary: summary || {},
        shopName: summary.shopName || user.shopName || '门店'
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      wx.hideNavigationBarLoading()
    }
  },
  go(e) {
    const url = e.currentTarget.dataset.url
    if (url.indexOf('/pages/home') === 0 || url.indexOf('/pages/customers') === 0
      || url.indexOf('/pages/transactions') === 0 || url.indexOf('/pages/shop') === 0) {
      wx.switchTab({ url })
    } else {
      wx.navigateTo({ url })
    }
  },
  onPullDownRefresh() {
    this.load().finally(() => wx.stopPullDownRefresh())
  }
})
