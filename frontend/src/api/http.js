import { ElMessage } from 'element-plus'
import router from '@/router/index.js'

/**
 * Unified API request helper.
 * @param {string} path
 * @param {RequestInit & { silent?: boolean }} options
 */
export async function request(path, options = {}) {
  const { silent = false, headers, ...rest } = options
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')

  let res
  try {
    res = await fetch(path, {
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers
      },
      ...rest
    })
  } catch {
    const msg = '无法连接服务器，请确认后端已在 8080 端口启动'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  let data
  try {
    data = await res.json()
  } catch {
    // 代理 502/后端未启动时常见 HTML 或空响应
    const msg =
      res.status === 502 || res.status === 504 || res.status === 500
        ? '后端服务不可用，请确认已启动并监听 8080'
        : '响应解析失败'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  if (!res.ok || data.success === false) {
    if (res.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      router.push('/login')
    }
    const msg = data.message || '请求失败'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  return data.data
}

export async function requestBlob(path) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const res = await fetch(path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    throw new Error('导出失败')
  }
  return res.blob()
}

/**
 * multipart/form-data 上传（不要手动设置 Content-Type，由浏览器带 boundary）。
 * @param {string} path
 * @param {FormData} formData
 * @param {RequestInit & { silent?: boolean }} options
 */
export async function requestForm(path, formData, options = {}) {
  const { silent = false, headers, ...rest } = options
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')

  let res
  try {
    res = await fetch(path, {
      method: 'POST',
      headers: {
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...headers
      },
      body: formData,
      ...rest
    })
  } catch {
    const msg = '无法连接服务器，请确认后端已在 8080 端口启动'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  let data
  try {
    data = await res.json()
  } catch {
    const msg =
      res.status === 502 || res.status === 504 || res.status === 500
        ? '后端服务不可用，请确认已启动并监听 8080'
        : '响应解析失败'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  if (!res.ok || data.success === false) {
    if (res.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      sessionStorage.removeItem('token')
      sessionStorage.removeItem('user')
      router.push('/login')
    }
    const msg = data.message || '请求失败'
    if (!silent) ElMessage.error(msg)
    throw new Error(msg)
  }

  return data.data
}
