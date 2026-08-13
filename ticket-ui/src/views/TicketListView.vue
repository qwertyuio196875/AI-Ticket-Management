<script setup lang="ts">
/**
 * 工单列表页 /tickets：
 * - 筛选栏：状态（字典 status）/ 优先级（字典 priority）/ 分类（工单分类）/ 处理人（用户搜索，无 user:manage 时隐藏）/ 日期范围
 * - 表格 + 分页；列显隐设置（localStorage 持久化）
 * - 导出（ticket:view）/ 新建（ticket:create）；行点击进详情
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { Plus, Download, Setting } from '@element-plus/icons-vue'
import { getTicketList, exportTickets, type TicketVO, type PageVO } from '../api/tickets'
import { getDictsByType, type SysDictVO } from '../api/dicts'
import { getEnabledCategories, type TicketCategoryVO } from '../api/ticketCategories'
import { getUserList, type SysUserVO } from '../api/users'
import { useAuthStore } from '../stores/auth'
import { buildTicketQueryParams, type TicketFilterForm } from '../utils/ticketQuery'
import { STATUS_LABELS, STATUS_TAG_TYPES, PRIORITY_LABELS, PRIORITY_TAG_TYPES } from '../utils/ticketState'
import { formatDateTime } from '../utils/format'

const router = useRouter()
const store = useAuthStore()

/** 是否拥有用户管理权限（决定处理人筛选项与名字映射的可用性） */
const canManageUsers = computed(() => store.hasAnyPermission(['user:manage']))

// ============ 筛选与数据 ============
const filterForm = reactive<TicketFilterForm>({
  status: undefined,
  priority: undefined,
  type: undefined,
  handlerId: null,
  dateRange: null,
})

const statusOptions = ref<SysDictVO[]>([])
const priorityOptions = ref<SysDictVO[]>([])
const categoryOptions = ref<TicketCategoryVO[]>([])
const userOptions = ref<SysUserVO[]>([])
const userMap = ref(new Map<number, string>())

const loading = ref(false)
const exporting = ref(false)
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const records = ref<TicketVO[]>([])

// ============ 列设置（localStorage 持久化） ============
const COLUMN_STORAGE_KEY = 'ticket-list.columns'
const columnMeta: Array<{ key: string; label: string; width?: number; defaultOn: boolean }> = [
  { key: 'ticketNo', label: '工单编号', width: 150, defaultOn: true },
  { key: 'title', label: '标题', defaultOn: true },
  { key: 'type', label: '分类', width: 110, defaultOn: true },
  { key: 'priority', label: '优先级', width: 90, defaultOn: true },
  { key: 'status', label: '状态', width: 100, defaultOn: true },
  { key: 'handler', label: '处理人', width: 100, defaultOn: true },
  { key: 'creator', label: '创建人', width: 100, defaultOn: true },
  { key: 'createTime', label: '创建时间', width: 160, defaultOn: true },
]

/** 当前启用的列 key 列表（从 localStorage 恢复，缺省全部显示） */
const visibleColumns = ref<string[]>(loadVisibleColumns())

function loadVisibleColumns(): string[] {
  try {
    const saved = localStorage.getItem(COLUMN_STORAGE_KEY)
    if (!saved) return columnMeta.map((c) => c.key)
    const parsed = JSON.parse(saved) as string[]
    // 与列定义求并集，避免旧存储缺列
    return columnMeta.map((c) => c.key).filter((k) => parsed.includes(k))
  } catch {
    return columnMeta.map((c) => c.key)
  }
}

function persistVisibleColumns() {
  localStorage.setItem(COLUMN_STORAGE_KEY, JSON.stringify(visibleColumns.value))
}

const columnSettingsVisible = ref(false)

function toggleColumn(key: string) {
  if (visibleColumns.value.includes(key)) {
    visibleColumns.value = visibleColumns.value.filter((k) => k !== key)
  } else {
    visibleColumns.value = [...visibleColumns.value, key]
  }
  persistVisibleColumns()
}

// ============ 数据加载 ============
async function loadFilterOptions() {
  const [statuses, priorities, categories] = await Promise.all([
    getDictsByType('status').catch(() => []),
    getDictsByType('priority').catch(() => []),
    getEnabledCategories().catch(() => []),
  ])
  statusOptions.value = statuses
  priorityOptions.value = priorities
  categoryOptions.value = categories
  if (canManageUsers.value) {
    const page = await getUserList({ pageNum: 1, pageSize: 50 }).catch(() => null)
    userOptions.value = page?.records ?? []
    userMap.value = new Map((page?.records ?? []).map((u) => [u.id, u.nickname || u.username]))
  }
}

async function loadTickets() {
  loading.value = true
  try {
    const params = buildTicketQueryParams(filterForm, pageNum.value, pageSize.value)
    const page: PageVO<TicketVO> = await getTicketList(params)
    records.value = page.records
    total.value = page.total
  } catch {
    // 错误已由 http 拦截器统一提示
  } finally {
    loading.value = false
  }
}

/** 查询：重置回第一页 */
function handleSearch() {
  pageNum.value = 1
  loadTickets()
}

function handleReset() {
  filterForm.status = undefined
  filterForm.priority = undefined
  filterForm.type = undefined
  filterForm.handlerId = null
  filterForm.dateRange = null
  handleSearch()
}

function handlePageChange(current: number) {
  pageNum.value = current
  loadTickets()
}

/** 导出当前筛选结果（沿用列表查询参数） */
async function handleExport() {
  exporting.value = true
  try {
    const params = buildTicketQueryParams(filterForm, pageNum.value, pageSize.value)
    await exportTickets(params)
  } catch {
    // 下载错误已由 download 内部提示
  } finally {
    exporting.value = false
  }
}

/** 行点击 → 工单详情 */
function handleRowClick(row: TicketVO) {
  router.push(`/tickets/${row.id}`)
}

/** 处理人 / 创建人展示：有用户权限时显示昵称，否则显示 ID */
function userName(id: number | null | undefined): string {
  if (id == null) return '-'
  return userMap.value.get(id) ?? `#${id}`
}

onMounted(() => {
  loadFilterOptions()
  loadTickets()
})

// ============ 列渲染辅助 ============
const columns = computed(() => columnMeta.filter((c) => visibleColumns.value.includes(c.key)))
</script>

<template>
  <div class="ticket-list">
    <!-- 筛选栏 -->
    <section class="filter-card">
      <el-form class="filter-form" :inline="true" @submit.prevent="handleSearch">
        <el-form-item label="状态">
          <el-select v-model="filterForm.status" placeholder="全部状态" clearable style="width: 120px">
            <el-option
              v-for="item in statusOptions"
              :key="item.id"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="优先级">
          <el-select v-model="filterForm.priority" placeholder="全部优先级" clearable style="width: 120px">
            <el-option
              v-for="item in priorityOptions"
              :key="item.id"
              :label="item.dictLabel"
              :value="item.dictValue"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="分类">
          <el-select v-model="filterForm.type" placeholder="全部分类" clearable style="width: 140px">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>

        <el-form-item v-if="canManageUsers" label="处理人">
          <el-select
            v-model="filterForm.handlerId"
            placeholder="全部处理人"
            clearable
            filterable
            style="width: 140px"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="创建时间">
          <el-date-picker
            v-model="filterForm.dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>

        <el-form-item class="filter-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </section>

    <!-- 工具栏 -->
    <section class="table-card">
      <div class="table-toolbar">
        <div class="toolbar-title">工单列表</div>
        <div class="toolbar-actions">
          <el-button :icon="Setting" @click="columnSettingsVisible = true">列设置</el-button>
          <el-button v-permission="['ticket:view']" :icon="Download" :loading="exporting" @click="handleExport">
            导出
          </el-button>
          <el-button v-permission="['ticket:create']" type="primary" :icon="Plus" @click="router.push('/tickets/create')">
            新建工单
          </el-button>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="records"
        class="ticket-table"
        @row-click="handleRowClick"
      >
        <el-table-column v-for="col in columns" :key="col.key" :prop="col.key" :label="col.label" :width="col.width">
          <template v-if="col.key === 'title'" #default="{ row }">
            <span class="ticket-title">{{ row.title }}</span>
          </template>

          <template v-else-if="col.key === 'priority'" #default="{ row }">
            <el-tag :type="PRIORITY_TAG_TYPES[row.priority as keyof typeof PRIORITY_TAG_TYPES]" size="small" effect="light">
              {{ PRIORITY_LABELS[row.priority as keyof typeof PRIORITY_LABELS] ?? row.priority }}
            </el-tag>
          </template>

          <template v-else-if="col.key === 'status'" #default="{ row }">
            <el-tag :type="STATUS_TAG_TYPES[row.status as keyof typeof STATUS_TAG_TYPES]" size="small" effect="light">
              {{ STATUS_LABELS[row.status as keyof typeof STATUS_LABELS] ?? row.status }}
            </el-tag>
          </template>

          <template v-else-if="col.key === 'handler'" #default="{ row }">
            {{ userName(row.handlerId) }}
          </template>

          <template v-else-if="col.key === 'creator'" #default="{ row }">
            {{ userName(row.creatorId) }}
          </template>

          <template v-else-if="col.key === 'createTime'" #default="{ row }">
            {{ formatDateTime(row.createTime) }}
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pageNum"
          :page-size="pageSize"
          :total="total"
          layout="total, prev, pager, next"
          background
          @current-change="handlePageChange"
        />
      </div>
    </section>

    <!-- 列设置弹窗 -->
    <el-dialog v-model="columnSettingsVisible" title="列设置" width="360px">
      <div class="column-settings">
        <el-checkbox
          v-for="col in columnMeta"
          :key="col.key"
          :model-value="visibleColumns.includes(col.key)"
          @change="toggleColumn(col.key)"
        >
          {{ col.label }}
        </el-checkbox>
      </div>
      <template #footer>
        <el-button @click="columnSettingsVisible = false">完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 筛选卡片：白底 + 细边框，Win11 风格 */
.filter-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 20px 0;
}

.filter-form {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 0 8px;
}

.filter-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.filter-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
}

.filter-actions {
  margin-left: auto;
}

/* 表格卡片 */
.table-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.toolbar-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.ticket-table {
  --el-table-border-color: var(--border-subtle);
  --el-table-header-bg-color: var(--bg-subtle);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-row-hover-bg-color: var(--accent-subtle);
  width: 100%;
}

.ticket-table :deep(.el-table__row) {
  cursor: pointer;
}

.ticket-title {
  color: var(--text-primary);
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--border-subtle);
}

/* 列设置弹窗 */
.column-settings {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.column-settings :deep(.el-checkbox__label) {
  color: var(--text-primary);
  font-size: 13px;
}

/* 响应式：窄屏筛选自动换行，工具栏折叠 */
/* 平板（768-1199）：筛选项两列网格，操作区占整行右对齐 */
@media (min-width: 768px) and (max-width: 1199px) {
  .filter-form {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 0 16px;
  }

  .filter-actions {
    grid-column: 1 / -1;
    display: flex;
    justify-content: flex-end;
  }
}

@media (max-width: 767px) {
  .table-toolbar {
    flex-direction: column;
    align-items: flex-start;
  }

  .filter-actions {
    margin-left: 0;
  }
}
</style>
