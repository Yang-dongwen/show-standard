export function money(v) {
  const n = Number(v || 0)
  if (Number.isNaN(n)) return '0.00'
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export function moneyShort(v) {
  const n = Number(v || 0)
  if (n >= 10000) return (n / 10000).toFixed(1) + '万'
  return money(v)
}

export function formatDateTime(v) {
  if (!v) return '—'
  const s = String(v).replace('T', ' ')
  return s.length > 19 ? s.slice(0, 19) : s
}

export function planLabel(code) {
  const map = { free: '免费版', plus: 'Plus', pro: 'Pro' }
  return map[code] || code || '—'
}

export function roleLabel(role) {
  return role === 'saas' ? '平台运营' : role || '运营'
}
