<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>邀请码</h1>
        <p class="page-desc">控制谁能开店 · 用量与吊销</p>
      </div>
      <div class="page-header-actions">
        <el-button type="primary" :icon="Plus" @click="showCreate = true">生成邀请码</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="hero-banner">
      <div>
        <div class="hero-title">商家自助开店</div>
        <div class="hero-desc">生成邀请码后发给商户，对方在「开店入口」填写即可开通门店。</div>
      </div>
      <el-button plain @click="$router.push('/open-shop')">预览开店页</el-button>
    </div>

    <div class="saas-table-wrap" v-loading="loading">
      <el-table :data="invites" stripe empty-text="暂无邀请码">
        <el-table-column label="邀请码" min-width="160">
          <template #default="{ row }">
            <div class="code-cell">
              <code>{{ row.code }}</code>
              <el-button link type="primary" size="small" @click="copy(row.code)">复制</el-button>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="用量" width="120">
          <template #default="{ row }">
            <div class="usage">
              <el-progress
                :percentage="usagePct(row)"
                :stroke-width="8"
                :show-text="false"
                :status="row.used_count >= row.max_uses ? 'success' : undefined"
              />
              <span>{{ row.used_count }} / {{ row.max_uses }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'active' ? 'success' : 'info'"
              size="small"
              effect="light"
              round
            >{{ row.status === 'active' ? '有效' : row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 'active'"
              link
              type="danger"
              @click="onRevoke(row)"
            >吊销</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="showCreate" title="生成邀请码" width="420px" align-center destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="可用次数">
          <el-input-number v-model="createForm.maxUses" :min="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="有效天数（0 表示不限制）">
          <el-input-number v-model="createForm.expireDays" :min="0" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.note" placeholder="渠道 / 客户备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreate = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="onCreate">生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createInvite, fetchInvites, revokeInvite } from '@/api/http.js'

const loading = ref(false)
const invites = ref([])
const showCreate = ref(false)
const creating = ref(false)
const createForm = reactive({ maxUses: 1, expireDays: 30, note: '' })

function usagePct(row) {
  const max = Number(row.max_uses) || 1
  const used = Number(row.used_count) || 0
  return Math.min(100, Math.round((used / max) * 100))
}

async function load() {
  loading.value = true
  try {
    invites.value = (await fetchInvites()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  creating.value = true
  try {
    const data = await createInvite({ ...createForm })
    showCreate.value = false
    ElMessage.success(`邀请码：${data.code}`)
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    creating.value = false
  }
}

async function onRevoke(row) {
  await ElMessageBox.confirm(`吊销邀请码 ${row.code}？`, '确认')
  await revokeInvite(row.id)
  ElMessage.success('已吊销')
  load()
}

async function copy(text) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.info(text)
  }
}

onMounted(load)
</script>

<style scoped>
.hero-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 18px 20px;
  margin-bottom: 16px;
  border-radius: var(--saas-radius-lg);
  background:
    radial-gradient(circle at 100% 0%, rgba(99, 102, 241, 0.18), transparent 45%),
    linear-gradient(135deg, #0f172a, #1e1b4b);
  color: #e2e8f0;
  border: 1px solid rgba(255, 255, 255, 0.06);
}
.hero-title { font-weight: 700; font-size: 15px; }
.hero-desc { margin-top: 4px; font-size: 12px; color: #94a3b8; max-width: 480px; line-height: 1.5; }
.code-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.code-cell code {
  font-family: ui-monospace, Consolas, monospace;
  background: #f1f5f9;
  padding: 4px 8px;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #4338ca;
}
.usage {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: var(--saas-text-2);
}
</style>
