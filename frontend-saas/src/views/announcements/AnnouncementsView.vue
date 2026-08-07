<template>
  <div class="page-shell">
    <div class="page-header">
      <div>
        <h1>公告下发</h1>
        <p class="page-desc">全网或指定门店 · C 端经营总览可见</p>
      </div>
      <div class="page-header-actions">
        <el-button type="primary" :icon="Plus" @click="showAnn = true">发布公告</el-button>
        <el-button :icon="Refresh" :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <div class="list" v-loading="loading">
      <div v-if="!announcements.length" class="empty-card">
        <div class="empty-title">还没有公告</div>
        <div class="empty-desc">发布后，活跃门店可在 C 端经营总览看到通知。</div>
        <el-button type="primary" @click="showAnn = true">发布第一条</el-button>
      </div>

      <article
        v-for="row in announcements"
        :key="row.id"
        class="ann-card"
        :class="{ inactive: row.status !== 'active' }"
      >
        <div class="ann-top">
          <div class="ann-title">{{ row.title }}</div>
          <div class="ann-tags">
            <el-tag size="small" effect="plain" round>
              {{ row.scope === 'all' ? '全网' : '指定店' }}
            </el-tag>
            <el-tag
              size="small"
              :type="row.status === 'active' ? 'success' : 'info'"
              effect="light"
              round
            >{{ row.status === 'active' ? '展示中' : '已下架' }}</el-tag>
          </div>
        </div>
        <p class="ann-body">{{ row.content }}</p>
        <div class="ann-foot">
          <span>{{ row.created_by || '—' }} · {{ row.created_at }}</span>
          <el-button
            v-if="row.status === 'active'"
            link
            type="danger"
            @click="onRevoke(row)"
          >下架</el-button>
        </div>
      </article>
    </div>

    <el-dialog v-model="showAnn" title="发布公告" width="520px" align-center destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="annForm.title" maxlength="128" show-word-limit placeholder="简明标题" />
        </el-form-item>
        <el-form-item label="内容">
          <el-input
            v-model="annForm.content"
            type="textarea"
            :rows="5"
            maxlength="1000"
            show-word-limit
            placeholder="门店可见的正文"
          />
        </el-form-item>
        <el-form-item label="范围">
          <el-radio-group v-model="annForm.scope">
            <el-radio-button value="all">全网</el-radio-button>
            <el-radio-button value="tenant">指定门店</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="annForm.scope === 'tenant'" label="门店租户 ID">
          <el-input v-model="annForm.tenantId" placeholder="租户数字 ID" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAnn = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onCreate">发布</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { createAnnouncement, fetchAnnouncements, revokeAnnouncement } from '@/api/http.js'

const loading = ref(false)
const announcements = ref([])
const showAnn = ref(false)
const saving = ref(false)
const annForm = reactive({ title: '', content: '', scope: 'all', tenantId: '' })

async function load() {
  loading.value = true
  try {
    announcements.value = (await fetchAnnouncements()) || []
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  if (!annForm.title.trim() || !annForm.content.trim()) {
    ElMessage.warning('请填写标题和内容')
    return
  }
  saving.value = true
  try {
    await createAnnouncement({ ...annForm })
    showAnn.value = false
    annForm.title = ''
    annForm.content = ''
    annForm.scope = 'all'
    annForm.tenantId = ''
    ElMessage.success('已发布')
    load()
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    saving.value = false
  }
}

async function onRevoke(row) {
  await ElMessageBox.confirm(`下架「${row.title}」？`)
  await revokeAnnouncement(row.id)
  ElMessage.success('已下架')
  load()
}

onMounted(load)
</script>

<style scoped>
.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 120px;
}
.empty-card {
  text-align: center;
  padding: 56px 24px;
  background: #fff;
  border: 1px dashed var(--saas-border);
  border-radius: var(--saas-radius-lg);
}
.empty-title { font-weight: 700; font-size: 16px; }
.empty-desc { color: var(--saas-text-3); font-size: 13px; margin: 8px 0 16px; }
.ann-card {
  background: #fff;
  border: 1px solid var(--saas-border);
  border-radius: var(--saas-radius-lg);
  padding: 18px 20px;
  box-shadow: var(--saas-shadow-xs);
  border-left: 4px solid #6366f1;
}
.ann-card.inactive {
  opacity: 0.65;
  border-left-color: #cbd5e1;
}
.ann-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.ann-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--saas-text);
}
.ann-tags { display: flex; gap: 6px; flex-shrink: 0; }
.ann-body {
  margin: 10px 0 14px;
  font-size: 13px;
  color: var(--saas-text-2);
  line-height: 1.65;
  white-space: pre-wrap;
}
.ann-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 12px;
  color: var(--saas-text-3);
}
</style>
