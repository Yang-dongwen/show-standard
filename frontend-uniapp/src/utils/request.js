import { baseUrl } from './config'
import { getToken, clearSession } from './auth'

/**
 * 统一请求封装
 * @param {string} path 以 /api 开头的路径
 * @param {{ method?: string, data?: object, header?: object, silent?: boolean }} options
 * @returns {Promise<any>} 解析后的 body.data
 */
export function request(path, options = {}) {
  const token = getToken()
  const method = (options.method || 'GET').toUpperCase()
  const override = uni.getStorageSync('baseUrlOverride')
  const root = (override || baseUrl || '').replace(/\/$/, '')

  return new Promise((resolve, reject) => {
    uni.request({
      url: root + path,
      method,
      data: options.data || {},
      header: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: 'Bearer ' + token } : {}),
        ...(options.header || {}),
      },
      success(res) {
        const body = res.data || {}
        if (res.statusCode === 401) {
          clearSession()
          uni.reLaunch({ url: '/pages/login/login' })
          reject(new Error(body.message || '未登录'))
          return
        }
        if (res.statusCode >= 400 || body.success === false) {
          const msg = body.message || '请求失败'
          if (!options.silent) {
            uni.showToast({ title: msg, icon: 'none', duration: 2500 })
          }
          reject(new Error(msg))
          return
        }
        resolve(body.data)
      },
      fail(err) {
        const msg = (err && err.errMsg) || '网络错误'
        if (!options.silent) {
          uni.showToast({ title: msg, icon: 'none' })
        }
        reject(new Error(msg))
      },
    })
  })
}

/**
 * 分页响应归一：兼容 { items, total, totalPages } / records 等
 */
export function normalizePage(data) {
  if (!data) {
    return { items: [], total: 0, totalPages: 0 }
  }
  const items = data.items || data.records || data.list || []
  const total = data.total != null ? data.total : items.length
  const totalPages =
    data.totalPages != null
      ? data.totalPages
      : data.size
        ? Math.ceil(total / data.size)
        : 1
  return { items, total, totalPages }
}

export default { request, normalizePage }
