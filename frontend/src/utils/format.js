export function money(value) {
  return new Intl.NumberFormat('zh-CN', { style: 'currency', currency: 'CNY' }).format(Number(value || 0))
}

export function dateTime(v) {
  if (!v) return ''
  const d = new Date(v)
  const year = d.getFullYear()
  const month = (d.getMonth() + 1).toString().padStart(2, '0')
  const day = d.getDate().toString().padStart(2, '0')
  const hours = d.getHours().toString().padStart(2, '0')
  const minutes = d.getMinutes().toString().padStart(2, '0')
  const seconds = d.getSeconds().toString().padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

export function last4(phone) {
  const p = String(phone || '').trim()
  return p.length >= 4 ? p.slice(-4) : ''
}

export function dateOffset(days) {
  const d = new Date()
  d.setDate(d.getDate() + days)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

export function formatChineseDate(date = new Date()) {
  const week = ['日', '一', '二', '三', '四', '五', '六']
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日 星期${week[date.getDay()]}`
}

export function slicePage(list, page, size) {
  const p = Math.max(1, page)
  const s = Math.max(1, size)
  const from = (p - 1) * s
  return list.slice(from, from + s)
}
