function requireLogin() {
  const token = wx.getStorageSync('token')
  if (!token) {
    wx.reLaunch({ url: '/pages/login/login' })
    return false
  }
  return true
}

function saveSession(data) {
  wx.setStorageSync('token', data.token)
  wx.setStorageSync('user', data.user || {})
  const app = getApp()
  if (app) app.globalData.user = data.user || {}
}

function clearSession() {
  wx.removeStorageSync('token')
  wx.removeStorageSync('user')
  const app = getApp()
  if (app) app.globalData.user = null
}

module.exports = { requireLogin, saveSession, clearSession }
