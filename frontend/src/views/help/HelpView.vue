<template>
  <div v-loading="loading" class="page-view">
    <el-alert
      v-if="accessInfo.url"
      type="info"
      show-icon
      :closable="false"
      class="access-alert"
      title="友情提示"
      :description="accessDesc"
    />
    <el-alert
      v-if="accessInfo.editionLabel"
      type="success"
      show-icon
      :closable="false"
      class="access-alert"
      :title="`当前产品：${accessInfo.editionLabel}`"
      :description="editionDesc"
    />

    <el-card shadow="never" class="help-card">
      <template #header>
        <span class="card-title">使用帮助</span>
      </template>
      <el-collapse v-model="activeNames">
        <el-collapse-item title="1. 添加客户（会员）" name="1">
          <p>进入「会员管理」，点击「新增会员」填写姓名、手机号后保存。校验码可手动填写；如不填写，系统默认使用手机号后四位。校验码用于到店消费口头核对，<strong>不等于支付密码</strong>。</p>
        </el-collapse-item>
        <el-collapse-item title="2. 添加员工" name="2">
          <p>进入「员工管理」，输入员工姓名并保存。停用员工不会删除历史数据，恢复后可继续使用。</p>
        </el-collapse-item>
        <el-collapse-item title="3. 会员充值" name="3">
          <p>进入「充值消费」中的「会员充值」，在下拉框输入姓名或手机号选择会员，填写金额后确认充值。</p>
        </el-collapse-item>
        <el-collapse-item title="4. 会员扣费（消费）" name="4">
          <p>在「会员消费」中选择会员、员工和服务，输入消费金额与校验码后确认。校验码需与会员档案一致才可扣费。错误次数会记入审计日志。</p>
        </el-collapse-item>
        <el-collapse-item title="5. 修改密码" name="5">
          <p>点击右上角用户菜单，输入旧密码和新密码完成修改。修改成功后需重新登录。</p>
        </el-collapse-item>
        <el-collapse-item title="6. 导出数据" name="6">
          <p>在会员、交易、报表页可导出 CSV，浏览器会直接下载。会员导出<strong>不含校验码</strong>。</p>
        </el-collapse-item>
        <el-collapse-item title="7. 桌面安装包退出" name="7">
          <p>若使用 Windows 桌面安装版：关闭浏览器窗口<strong>不会</strong>停止后台服务。请从任务栏托盘图标右键选择 <strong>Exit Show</strong> 真正退出。再次双击快捷方式会重新打开界面（单实例）。</p>
        </el-collapse-item>
        <el-collapse-item title="8. 登录账号（用户名）" name="8">
          <p>门店登录用户名在<strong>全平台全局唯一</strong>（云版多店不能都叫 admin）。开店或新增店员账号时请使用可区分的用户名。</p>
        </el-collapse-item>
        <el-collapse-item title="9. 校验码可见性" name="9">
          <p>默认仅<strong>店长</strong>可在会员列表中查看/点开校验码明文；收银/店员列表显示为「保密」。消费时由会员口述校验码录入即可。</p>
        </el-collapse-item>
      </el-collapse>

      <el-alert
        class="danger-tip"
        type="warning"
        show-icon
        :closable="false"
        title="重要提示"
        description="本地版数据在用户目录 ~/.show（Windows 多为 C:\\Users\\{用户}\\.show），请勿删除该目录及数据库文件。"
      />
    </el-card>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, reactive, ref } from 'vue'
import { fetchAccessInfo } from '@/api/system.js'

const loading = ref(false)
const activeNames = ref(['1', '2', '3'])
const accessInfo = reactive({
  ip: '',
  port: '',
  url: '',
  edition: '',
  editionLabel: '',
  licenseModel: '',
  db: '',
  saasEnabled: false,
  miniProgramEnabled: false
})
const accessDesc = computed(
  () =>
    `当前服务地址：${accessInfo.ip}:${accessInfo.port}，可在浏览器打开 ${accessInfo.url} 访问 Web 页面。桌面安装版请从托盘 Exit 退出服务。`
)
const editionDesc = computed(() => {
  if (accessInfo.edition === 'saas' || accessInfo.licenseModel === 'subscription') {
    return `SaaS 云版（订阅）：数据 ${accessInfo.db || 'mysql'}；支持运营台与小程序（若已开通）。店长用户名全平台唯一。`
  }
  return `本地买断版：数据存本机 ${accessInfo.db || 'sqlite'}（~/.show），不支持云端 MySQL、SaaS 运营台与微信小程序。`
})

const registerRefresh = inject('registerRefresh', null)
let unregister = null

async function load() {
  loading.value = true
  try {
    Object.assign(accessInfo, await fetchAccessInfo())
  } catch {
    // optional
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await load()
  if (registerRefresh) unregister = registerRefresh(load)
})
onUnmounted(() => {
  if (unregister) unregister()
})
</script>

<style scoped>
.access-alert {
  border-radius: 10px;
}

.help-card {
  border-radius: 12px;
  border: 1px solid var(--border);
}

.card-title {
  font-weight: 600;
}

.help-card p {
  color: var(--text-secondary);
  line-height: 1.7;
  font-size: 14px;
}

.danger-tip {
  margin-top: 20px;
  border-radius: 10px;
}
</style>
