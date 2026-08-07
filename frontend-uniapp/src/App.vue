<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { getUser } from '@/utils/auth'

onLaunch(() => {
  const user = getUser()
  if (user) {
    // 启动时恢复本地会话（业务页 onShow 再 requireLogin）
    console.log('[Show] session restored', user.username || user.name || '')
  }
})

onShow(() => {
  console.log('[Show] App Show')
})

onHide(() => {
  console.log('[Show] App Hide')
})
</script>

<style lang="scss">
@import '@/styles/app.scss';
</style>
