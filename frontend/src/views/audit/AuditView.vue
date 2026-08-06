<template>
  <div class="page-view">
    <div class="page-toolbar">
      <div class="page-toolbar-left">
        <el-input
          v-model="listKeyword"
          class="field-lg"
          clearable
          placeholder="动作/实体/详情"
          :prefix-icon="Search"
          @keyup.enter="reload"
          @clear="reload"
        />
        <el-button type="primary" @click="reload">查询</el-button>
      </div>
    </div>

    <el-card shadow="never" class="table-card" v-loading="loading">
      <el-table :data="pagedLogs" stripe height="100%">
        <template #empty>
          <EmptyHint description="暂无审计日志" />
        </template>
        <el-table-column label="时间" min-width="170">
          <template #default="{ row }">{{ dateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column prop="action" label="动作" min-width="120" />
        <el-table-column prop="entityType" label="实体" min-width="120" />
        <el-table-column prop="detail" label="详情" min-width="260" show-overflow-tooltip />
      </el-table>
      <div class="pager-bar">
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          background
          layout="total, prev, pager, next"
          :total="logs.length"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, inject, onMounted, onUnmounted, ref, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { fetchAuditLogs } from '@/api/audit.js'
import { dateTime, slicePage } from '@/utils/format.js'
import { debounce } from '@/utils/debounce.js'
import EmptyHint from '@/components/common/EmptyHint.vue'

const loading = ref(false)
const listKeyword = ref('')
const logs = ref([])
const page = ref(1)
const size = 10

const pagedLogs = computed(() => slicePage(logs.value, page.value, size))
const registerRefresh = inject('registerRefresh', null)
let unregister = null

const debouncedReload = debounce(() => {
  page.value = 1
  load()
}, 320)

watch(listKeyword, () => debouncedReload())

async function load() {
  loading.value = true
  try {
    logs.value = await fetchAuditLogs({ keyword: listKeyword.value.trim() })
    page.value = 1
  } finally {
    loading.value = false
  }
}

function reload() {
  debouncedReload.cancel()
  page.value = 1
  load()
}

onMounted(async () => {
  await load()
  if (registerRefresh) unregister = registerRefresh(load)
})
onUnmounted(() => {
  debouncedReload.cancel()
  if (unregister) unregister()
})
</script>
