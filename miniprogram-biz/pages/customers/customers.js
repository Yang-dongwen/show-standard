const { request } = require('../../utils/request')
const { requireLogin } = require('../../utils/auth')

Page({
  data: {
    keyword: '',
    items: [],
    page: 1,
    totalPages: 1,
    loading: false
  },
  onShow() {
    if (!requireLogin()) return
    this.reload()
  },
  onKeyword(e) {
    this.setData({ keyword: e.detail.value })
  },
  search() {
    this.reload()
  },
  async reload() {
    this.setData({ page: 1 })
    await this.load()
  },
  async load() {
    this.setData({ loading: true })
    try {
      const data = await request(
        `/api/customers?keyword=${encodeURIComponent(this.data.keyword || '')}&page=${this.data.page}&size=20`
      )
      this.setData({
        items: data.items || data.list || data.records || [],
        totalPages: data.totalPages || 1
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
  goEdit(e) {
    const id = e.currentTarget.dataset.id || ''
    wx.navigateTo({
      url: id ? `/pages/customer-edit/customer-edit?id=${id}` : '/pages/customer-edit/customer-edit'
    })
  },
  more() {
    if (this.data.page >= this.data.totalPages) return
    this.setData({ page: this.data.page + 1 })
    this.loadMore()
  },
  async loadMore() {
    try {
      const data = await request(
        `/api/customers?keyword=${encodeURIComponent(this.data.keyword || '')}&page=${this.data.page}&size=20`
      )
      const more = data.items || []
      this.setData({
        items: this.data.items.concat(more),
        totalPages: data.totalPages || 1
      })
    } catch (e) {
      wx.showToast({ title: e.message || '加载失败', icon: 'none' })
    }
  }
})
