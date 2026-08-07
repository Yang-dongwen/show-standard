const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')

Page({
  data: {
    id: '',
    name: '',
    phone: '',
    verifyCode: '',
    remark: '',
    initialRechargeAmount: '',
    saving: false
  },
  onLoad(q) {
    if (!requireLogin()) return
    if (q.id) {
      this.setData({ id: q.id })
      this.loadOne(q.id)
    }
  },
  async loadOne(id) {
    try {
      // 分页接口无单条时：列表搜 id 简化——用 keyword 空拉后找；或直接编辑已有字段由列表传入
      // 这里用列表接口 page 全量查找（小数据可接受）
      const data = await request('/api/customers?page=1&size=200')
      const items = data.items || []
      const c = items.find((x) => String(x.id) === String(id))
      if (c) {
        this.setData({
          name: c.name || '',
          phone: c.phone || '',
          verifyCode: c.verifyCode || c.verify_code || '',
          remark: c.remark || ''
        })
      }
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  },
  onField(e) {
    const k = e.currentTarget.dataset.k
    this.setData({ [k]: e.detail.value })
  },
  async save() {
    const { id, name, phone, verifyCode, remark, initialRechargeAmount } = this.data
    if (!name || !phone) {
      wx.showToast({ title: '姓名和手机号必填', icon: 'none' })
      return
    }
    if (String(name).trim().length > 32) {
      wx.showToast({ title: '会员姓名最多32个字', icon: 'none' })
      return
    }
    if (remark && String(remark).trim().length > 200) {
      wx.showToast({ title: '备注最多200个字', icon: 'none' })
      return
    }
    if (!/^1\d{10}$/.test(String(phone).trim())) {
      wx.showToast({ title: '手机号须为11位且以1开头', icon: 'none' })
      return
    }
    if (verifyCode && !/^\d{4}$/.test(String(verifyCode).trim())) {
      wx.showToast({ title: '校验码须为4位数字', icon: 'none' })
      return
    }
    this.setData({ saving: true })
    try {
      const body = {
        name,
        phone,
        verifyCode: verifyCode || undefined,
        remark
      }
      if (!id && initialRechargeAmount) {
        body.initialRechargeAmount = Number(initialRechargeAmount)
      }
      if (id) {
        await request(`/api/customers/${id}`, { method: 'PUT', data: body })
      } else {
        await request('/api/customers', { method: 'POST', data: body })
      }
      wx.showToast({ title: '已保存', icon: 'success' })
      setTimeout(() => wx.navigateBack(), 400)
    } catch (e) {
      wx.showToast({ title: e.message || '保存失败', icon: 'none' })
    } finally {
      this.setData({ saving: false })
    }
  }
})
