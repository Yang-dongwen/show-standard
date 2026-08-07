const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')

Page({
  data: {
    mode: 'consume',
    customers: [],
    employees: [],
    services: [],
    customerId: '',
    customerName: '',
    employeeId: '',
    employeeName: '',
    serviceTypeId: '',
    serviceName: '',
    amount: '',
    verifyCode: '',
    remark: '',
    rows: [],
    submitting: false
  },
  onShow() {
    if (!requireLogin()) return
    this.bootstrap()
  },
  async bootstrap() {
    try {
      const [cust, emp, svc, list] = await Promise.all([
        request('/api/customers?page=1&size=100'),
        request('/api/employees/options'),
        request('/api/config/services/options'),
        request('/api/transactions?page=1&size=15')
      ])
      this.setData({
        customers: cust.items || [],
        employees: emp || [],
        services: svc || [],
        rows: (list && list.items) || []
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },
  setMode(e) {
    this.setData({ mode: e.currentTarget.dataset.mode })
  },
  pickCustomer() {
    const names = this.data.customers.map((c) => `${c.name} ${c.phone}`)
    if (!names.length) {
      wx.showToast({ title: '暂无会员', icon: 'none' })
      return
    }
    wx.showActionSheet({
      itemList: names.slice(0, 6),
      success: (res) => {
        const c = this.data.customers[res.tapIndex]
        this.setData({ customerId: c.id, customerName: c.name })
      }
    })
  },
  pickEmployee() {
    const names = this.data.employees.map((e) => e.name || e.label)
    if (!names.length) return
    wx.showActionSheet({
      itemList: names.slice(0, 6),
      success: (res) => {
        const e = this.data.employees[res.tapIndex]
        this.setData({ employeeId: e.id || e.value, employeeName: e.name || e.label })
      }
    })
  },
  pickService() {
    const names = this.data.services.map((s) => `${s.name || s.label} ¥${s.price || ''}`)
    if (!names.length) return
    wx.showActionSheet({
      itemList: names.slice(0, 6),
      success: (res) => {
        const s = this.data.services[res.tapIndex]
        this.setData({
          serviceTypeId: s.id || s.value,
          serviceName: s.name || s.label,
          amount: String(s.price != null ? s.price : '')
        })
      }
    })
  },
  onField(e) {
    this.setData({ [e.currentTarget.dataset.k]: e.detail.value })
  },
  async submit() {
    const {
      mode, customerId, employeeId, serviceTypeId, amount, verifyCode, remark
    } = this.data
    if (!customerId || !amount) {
      wx.showToast({ title: '请选择会员并填写金额', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    try {
      if (mode === 'recharge') {
        await request('/api/transactions/recharge', {
          method: 'POST',
          data: { customerId, amount: Number(amount), remark }
        })
      } else {
        if (!employeeId || !serviceTypeId || !verifyCode) {
          wx.showToast({ title: '请完善员工/服务/校验码', icon: 'none' })
          this.setData({ submitting: false })
          return
        }
        await request('/api/transactions/consume', {
          method: 'POST',
          data: {
            customerId,
            employeeId,
            serviceTypeId,
            verifyCode,
            amount: Number(amount),
            remark
          }
        })
      }
      wx.showToast({ title: '成功', icon: 'success' })
      this.setData({ amount: '', verifyCode: '', remark: '' })
      this.bootstrap()
    } catch (e) {
      wx.showToast({ title: e.message || '失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  }
})
