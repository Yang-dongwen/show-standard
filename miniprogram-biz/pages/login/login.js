const { request } = require('../../utils/request')
const { saveSession } = require('../../utils/auth')
const { baseUrl, productNote } = require('../../utils/config')

Page({
  data: {
    username: '',
    password: '',
    loading: false,
    wxLoading: false,
    bindMode: false,
    preToken: '',
    productNote,
    baseUrl
  },
  onShow() {
    if (wx.getStorageSync('token')) {
      wx.switchTab({ url: '/pages/home/home' })
    }
  },
  onUser(e) {
    this.setData({ username: e.detail.value })
  },
  onPwd(e) {
    this.setData({ password: e.detail.value })
  },
  async onLogin() {
    const { username, password, bindMode, preToken } = this.data
    if (!username || !password) {
      wx.showToast({ title: '请输入账号密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      if (bindMode && preToken) {
        const data = await request('/api/auth/wx-bind', {
          method: 'POST',
          data: { preToken, username, password }
        })
        saveSession(data)
        wx.showToast({ title: '绑定并登录成功', icon: 'success' })
      } else {
        const data = await request('/api/auth/login', {
          method: 'POST',
          data: { username, password }
        })
        saveSession(data)
        wx.showToast({ title: '登录成功', icon: 'success' })
      }
      wx.switchTab({ url: '/pages/home/home' })
    } catch (e) {
      wx.showToast({ title: e.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  onWxLogin() {
    this.setData({ wxLoading: true })
    wx.login({
      success: async (res) => {
        if (!res.code) {
          wx.showToast({ title: 'wx.login 无 code', icon: 'none' })
          this.setData({ wxLoading: false })
          return
        }
        try {
          const data = await request('/api/auth/wx-login', {
            method: 'POST',
            data: { code: res.code }
          })
          if (data.bindRequired) {
            this.setData({
              bindMode: true,
              preToken: data.preToken || '',
              wxLoading: false
            })
            wx.showToast({ title: '请输入店长账号完成绑定', icon: 'none', duration: 2500 })
            return
          }
          saveSession(data)
          wx.showToast({ title: '微信登录成功', icon: 'success' })
          wx.switchTab({ url: '/pages/home/home' })
        } catch (e) {
          wx.showToast({ title: e.message || '微信登录失败', icon: 'none' })
        } finally {
          this.setData({ wxLoading: false })
        }
      },
      fail: () => {
        this.setData({ wxLoading: false })
        wx.showToast({ title: 'wx.login 失败', icon: 'none' })
      }
    })
  },
  cancelBind() {
    this.setData({ bindMode: false, preToken: '' })
  }
})
