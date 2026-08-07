const TOKEN_KEY = 'token'
const USER_KEY = 'user'

export function getToken() {
  return uni.getStorageSync(TOKEN_KEY) || ''
}

export function getUser() {
  return uni.getStorageSync(USER_KEY) || null
}

export function saveSession(data) {
  // 与 miniprogram-biz 对齐：登录成功体含 token 时写入；user 缺省为 {}
  uni.setStorageSync(TOKEN_KEY, (data && data.token) || '')
  uni.setStorageSync(USER_KEY, (data && data.user) || {})
}

export function clearSession() {
  uni.removeStorageSync(TOKEN_KEY)
  uni.removeStorageSync(USER_KEY)
}

/**
 * 业务页 onShow 调用：无 token 则 reLaunch 登录页
 * @returns {boolean} 是否已登录
 */
export function requireLogin() {
  const token = getToken()
  if (!token) {
    uni.reLaunch({ url: '/pages/login/login' })
    return false
  }
  return true
}

export default {
  getToken,
  getUser,
  saveSession,
  clearSession,
  requireLogin,
}
