function money(v) {
  const n = Number(v || 0)
  return n.toFixed(2)
}

function today() {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return d.getFullYear() + '-' + m + '-' + day
}

function daysAgo(n) {
  const d = new Date()
  d.setDate(d.getDate() - n)
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return d.getFullYear() + '-' + m + '-' + day
}

module.exports = { money, today, daysAgo }
