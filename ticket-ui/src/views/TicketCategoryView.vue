<script setup lang="ts">
/**
 * 工单分类页 /system/ticket-categories（category:manage）：
 * 表格（GET /manage 全量含禁用）+ CRUD（含启用/停用切换）+ 拖拽排序
 * 拖拽：HTML5 DnD → recalculateCategorySort 重算 sort → 受影响项逐个 PUT
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Rank } from '@element-plus/icons-vue'
import {
  getManageCategories, createCategory, updateCategory, deleteCategory,
  type TicketCategoryVO, type TicketCategorySaveParams,
} from '../api/ticketCategories'
import { recalculateCategorySort } from '../utils/categorySort'
import { formatDateTime } from '../utils/format'

const loading = ref(false)
const list = ref<TicketCategoryVO[]>([])

// ============ 拖拽排序状态 ============
const dragIndex = ref<number | null>(null)
const overIndex = ref<number | null>(null)

function handleDragStart(index: number) {
  dragIndex.value = index
}

function handleDragOver(e: DragEvent, index: number) {
  e.preventDefault()
  if (overIndex.value !== index) overIndex.value = index
}

function handleDragEnd() {
  dragIndex.value = null
  overIndex.value = null
}

async function handleDrop(e: DragEvent, index: number) {
  e.preventDefault()
  const from = dragIndex.value
  const to = index
  dragIndex.value = null
  overIndex.value = null
  if (from == null || from === to) return
  await applyReorder(from, to)
}

/** 重算 sort 并逐个 PUT 受影响项 */
async function applyReorder(from: number, to: number) {
  const changes = recalculateCategorySort(list.value, from, to)
  if (changes.length === 0) return

  // 本地乐观更新展示顺序
  const reordered = [...list.value]
  const [moved] = reordered.splice(from, 1)
  reordered.splice(to, 0, moved)
  list.value = reordered.map((item, index) => ({ ...item, sort: index + 1 }))

  try {
    for (const change of changes) {
      const target = reordered.find((c) => c.id === change.id)
      if (!target) continue
      await updateCategory({
        id: target.id,
        name: target.name,
        description: target.description,
        sort: change.sort,
        status: target.status,
      })
    }
    ElMessage.success('排序已保存')
  } catch {
    ElMessage.error('排序保存失败，已恢复原顺序')
    loadCategories()
  }
}

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  name: '',
  description: '',
  sort: 1,
  status: 1,
})

function resetForm() {
  editingId.value = null
  form.name = ''
  form.description = ''
  form.sort = list.value.length + 1
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: TicketCategoryVO) {
  editingId.value = row.id
  form.name = row.name
  form.description = row.description
  form.sort = row.sort
  form.status = row.status
  dialogVisible.value = true
}

async function save() {
  if (!form.name.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  saving.value = true
  try {
    const params: TicketCategorySaveParams = {
      id: editingId.value ?? undefined,
      name: form.name.trim(),
      description: form.description.trim() || undefined,
      sort: form.sort,
      status: form.status,
    }
    if (editingId.value == null) {
      await createCategory(params)
      ElMessage.success('分类创建成功')
    } else {
      await updateCategory(params)
      ElMessage.success('分类更新成功')
    }
    dialogVisible.value = false
    loadCategories()
  } catch {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

/** 启用 / 停用切换 */
async function handleToggleStatus(row: TicketCategoryVO) {
  try {
    await updateCategory({
      id: row.id,
      name: row.name,
      description: row.description,
      sort: row.sort,
      status: row.status === 1 ? 0 : 1,
    })
    ElMessage.success(row.status === 1 ? '分类已停用' : '分类已启用')
    loadCategories()
  } catch {
    // 错误已提示
  }
}

async function handleDelete(row: TicketCategoryVO) {
  try {
    await ElMessageBox.confirm(`确定删除分类「${row.name}」吗？`, '删除分类', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteCategory(row.id)
    ElMessage.success('分类已删除')
    loadCategories()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 查询 ============
async function loadCategories() {
  loading.value = true
  try {
    list.value = await getManageCategories()
  } catch {
    // 错误已提示
  } finally {
    loading.value = false
  }
}

onMounted(loadCategories)
</script>

<template>
  <div class="manage-page">
    <section class="table-card">
      <div class="table-toolbar">
        <div class="toolbar-left">
          <span class="toolbar-title">工单分类</span>
          <span class="toolbar-hint">拖动行调整显示顺序</span>
        </div>
        <el-button v-permission="['category:manage']" type="primary" :icon="Plus" @click="openCreate">新建分类</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        row-key="id"
        class="manage-table"
        :class="{ 'is-dragging': dragIndex != null }"
      >
        <el-table-column width="44" align="center">
          <template #default="{ $index }">
            <el-icon
              class="drag-handle"
              :class="{ 'drag-over': overIndex === $index }"
              :draggable="true"
              @dragstart.prevent="handleDragStart($index)"
              @dragover="handleDragOver($event, $index)"
              @drop="handleDrop($event, $index)"
              @dragend="handleDragEnd"
            >
              <Rank />
            </el-icon>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="名称" min-width="140" />
        <el-table-column prop="description" label="描述" min-width="200">
          <template #default="{ row }">{{ row.description || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['category:manage']" size="small" text @click="handleToggleStatus(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button v-permission="['category:manage']" size="small" :icon="Edit" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['category:manage']" size="small" :icon="Delete" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建分类' : '编辑分类'" width="440px">
      <el-form label-position="top">
        <el-form-item label="名称" required>
          <el-input v-model="form.name" maxlength="50" placeholder="如：网络问题（≤50 字）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="排序">
            <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
          <el-form-item label="状态">
            <el-radio-group v-model="form.status">
              <el-radio :value="1">启用</el-radio>
              <el-radio :value="0">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.manage-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

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

.toolbar-left {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.toolbar-hint {
  font-size: 12px;
  color: var(--text-tertiary);
}

.manage-table {
  --el-table-border-color: var(--border-subtle);
  --el-table-header-bg-color: var(--bg-subtle);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-row-hover-bg-color: var(--accent-subtle);
  width: 100%;
}

.drag-handle {
  cursor: grab;
  color: var(--text-tertiary);
  transition: color 0.15s ease, transform 0.15s ease;
}

.drag-handle:hover {
  color: var(--accent);
}

.drag-handle.drag-over {
  color: var(--accent);
  transform: scale(1.15);
}

.manage-table.is-dragging :deep(.el-table__row) {
  cursor: grabbing;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 767px) {
  .toolbar-left {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }

  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

/* 平板：工具条换行、表格内部横向滚动（页面级不溢出） */
@media (max-width: 1023px) {
  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .manage-table {
    min-width: 840px;
  }
}

/* 手机：对话框宽度适配 */
@media (max-width: 767px) {
  :deep(.el-dialog) {
    width: 92% !important;
  }
}
</style>
