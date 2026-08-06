import { ElMessage } from 'element-plus'
import { requestBlob } from '@/api/http.js'

/**
 * 带鉴权的浏览器文件下载。
 */
export async function downloadWithAuth(url, filename) {
  try {
    const blob = await requestBlob(url)
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = filename
    link.click()
    URL.revokeObjectURL(link.href)
    ElMessage.success('导出成功')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
    throw e
  }
}
