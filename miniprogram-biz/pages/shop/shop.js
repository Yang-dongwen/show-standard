const { request } = require('../../utils/request')
const { requireLogin, clearSession } = require('../../utils/auth')
const { productNote } = require('../../utils/config')

Page({
  data: {
    shop: {},
    shopName: '',
    user: {},
    wxBound: false,
    productNote
  },
  onShow() {
    if (!requireLogin()) return
    this.setData({ user: wx.getStorageSync('user') || {} })
    this.load()
  },
  async load() {
    try {
      const [shop, wxSt] = await Promise.all([
        request('/api/shop'),
        request('/api/auth/wx-bind-status').catch(() => ({ bound: false }))
      ])
      this.setData({
        shop: shop || {},
        shopName: (shop && shop.shopName) || '',
        wxBound: !!(wxSt && wxSt.bound)
      })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  },
  bindWx() {
    wx.login({
      success: async (res) => {
        if (!res.code) return
        try {
          // 已登录态下用 code 绑定：走 wx-bind 需密码。改为先 wx-login 若 bindRequired 则提示去登录页绑定
          // 已登录：用临时方案 — 重新调用 bind 需要密码。这里提示用户用「微信一键登录」完成绑定
          wx.showModal({
            title: '绑定微信',
            content: '请退出后使用「微信一键登录」，输入账号密码完成首次绑定。',
            showCancel: false
          })
        } catch (e) {
          wx.showToast({ title: e.message || '失败', icon: 'none' })
        }
      }
    })
  },
  async unbindWx() {
    try {
      await request('/api/auth/wx-unbind', { method: 'POST' })
      this.setData({ wxBound: false })
      wx.showToast({ title: '已解绑', icon: 'success' })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  },
  onName(e) {
    this.setData({ shopName: e.detail.value })
  },
  async save() {
    try {
      const shop = await request('/api/shop', {
        method: 'PUT',
        data: { shopName: this.data.shopName }
      })
      this.setData({ shop })
      const user = wx.getStorageSync('user') || {}
      user.shopName = shop.shopName
      wx.setStorageSync('user', user)
      wx.showToast({ title: '已保存', icon: 'success' })
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    }
  },
  go(e) {
    wx.navigateTo({ url: e.currentTarget.dataset.url })
  },
  logout() {
    clearSession()
    wx.reLaunch({ url: '/pages/login/login' })
  }
})
