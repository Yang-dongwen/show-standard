const { baseUrl } = require('./config')

function request(path, options = {}) {
  const token = wx.getStorageSync('token') || ''
  const method = (options.method || 'GET').toUpperCase()
  return new Promise((resolve, reject) => {
    wx.request({
      url: baseUrl + path,
      method,
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(options.header || {})
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401) {
          wx.removeStorageSync('token')
          wx.removeStorageSync('user')
          wx.reLaunch({ url: '/pages/login/login' })
          reject(new Error(body.message || '未登录'))
          return
        }
        if (res.statusCode >= 400 || body.success === false) {
          reject(new Error(body.message || '请求失败'))
          return
        }
        resolve(body.data)
      },
      fail(err) {
        reject(new Error(err.errMsg || '网络错误'))
      }
    })
  })
}

module.exports = { request }
