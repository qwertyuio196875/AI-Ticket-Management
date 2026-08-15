<script setup lang="ts">
/**
 * 数据字典页 /system/dicts（dict:manage）：
 * 按 dict_type 分 tab（priority / comment_type / status 种子类型；新建条目可输入新 dictType 并切到新 tab）
 * + 表格 CRUD（更新时 dictType / dictValue 禁编辑）
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getDictList, createDict, updateDict, deleteDict,
  type SysDictVO, type DictSaveParams,
} from '../api/dicts'
import { formatDateTime } from '../utils/format'

/** 种子 dictType（tab 初始列表） */
const SEED_TYPES = ['priority', 'comment_type', 'status']

const loading = ref(false)
const records = ref<SysDictVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

/** 当前激活 tab */
const activeType = ref(SEED_TYPES[0])

/** tab 列表：种子类型 + 查询过程中遇到的新类型 */
const tabs = ref<string[]>([...SEED_TYPES])

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  dictType: '',
  dictValue: '',
  dictLabel: '',
  sort: 1,
  status: 1,
  remark: '',
})

function resetForm() {
  editingId.value = null
  form.dictType = activeType.value
  form.dictValue = ''
  form.dictLabel = ''
  form.sort = 1
  form.status = 1
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: SysDictVO) {
  editingId.value = row.id
  form.dictType = row.dictType
  form.dictValue = row.dictValue
  form.dictLabel = row.dictLabel
  form.sort = row.sort
  form.status = row.status
  form.remark = row.remark
  dialogVisible.value = true
}

async function save() {
  if (!form.dictType.trim() || !form.dictValue.trim() || !form.dictLabel.trim()) {
    ElMessage.warning('请完整填写类型、值与标签')
    return
  }
  saving.value = true
  try {
    const params: DictSaveParams = {
      id: editingId.value ?? undefined,
      dictType: form.dictType.trim(),
      dictValue: form.dictValue.trim(),
      dictLabel: form.dictLabel.trim(),
      sort: form.sort,
      status: form.status,
      remark: form.remark.trim() || undefined,
    }
    if (editingId.value == null) {
      await createDict(params)
      ElMessage.success('字典条目创建成功')
      // 新建了全新 dictType：加入 tab 并切换过去
      if (!tabs.value.includes(params.dictType)) {
        tabs.value.push(params.dictType)
      }
      activeType.value = params.dictType
    } else {
      await updateDict(params)
      ElMessage.success('字典条目更新成功')
    }
    dialogVisible.value = false
    loadDicts()
  } catch {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SysDictVO) {
  try {
    await ElMessageBox.confirm(`确定删除字典条目「${row.dictLabel}」吗？`, '删除字典条目', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteDict(row.id)
    ElMessage.success('字典条目已删除')
    loadDicts()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 查询 ============
async function loadDicts() {
  loading.value = true
  try {
    const page = await getDictList({
      dictType: activeType.value,
      pageNum: pageNum.value,
      pageSize: pageSize.value,
    })
    records.value = page.records
    total.value = page.total
  } catch {
    // 错误已提示
  } finally {
    loading.value = false
  }
}

function handleTabChange(name: string | number) {
  activeType.value = String(name)
  pageNum.value = 1
  loadDicts()
}

onMounted(loadDicts)
</script>

<template>
  <div class="manage-page">
    <section class="table-card">
      <div class="table-toolbar">
        <span class="toolbar-title">数据字典</span>
        <el-button v-permission="['dict:manage']" type="primary" :icon="Plus" @click="openCreate">新建条目</el-button>
      </div>

      <el-tabs v-model="activeType" class="dict-tabs" @tab-change="handleTabChange">
        <el-tab-pane v-for="type in tabs" :key="type" :label="type" :name="type" />
      </el-tabs>

      <el-table v-loading="loading" :data="records" class="manage-table">
        <el-table-column prop="dictValue" label="字典值" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.dictValue }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="dictLabel" label="标签" min-width="120" />
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small" effect="light">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="160">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['dict:manage']" size="small" :icon="Edit" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['dict:manage']" size="small" :icon="Delete" text type="danger" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadDicts"
        />
      </div>
    </section>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建字典条目' : '编辑字典条目'" width="460px">
      <el-form label-position="top">
        <div class="form-row">
          <el-form-item label="字典类型" required>
            <el-input v-model="form.dictType" :disabled="editingId != null" placeholder="如：priority" />
          </el-form-item>
          <el-form-item label="字典值" required>
            <el-input v-model="form.dictValue" :disabled="editingId != null" placeholder="如：HIGH" />
          </el-form-item>
        </div>
        <el-form-item label="标签" required>
          <el-input v-model="form.dictLabel" placeholder="如：高" />
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
        <el-form-item label="备注">
          <el-input v-model="form.remark" maxlength="255" />
        </el-form-item>
        <div v-if="editingId != null" class="form-tip">编辑时字典类型与字典值不可修改</div>
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

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.dict-tabs {
  padding: 0 20px;
  border-bottom: 1px solid var(--border-subtle);
}

.dict-tabs :deep(.el-tabs__item) {
  color: var(--text-secondary);
  font-size: 13px;
}

.dict-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent);
}

.dict-tabs :deep(.el-tabs__active-bar) {
  background-color: var(--accent);
}

.manage-table {
  --el-table-border-color: var(--border-subtle);
  --el-table-header-bg-color: var(--bg-subtle);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-row-hover-bg-color: var(--accent-subtle);
  width: 100%;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  padding: 14px 20px;
  border-top: 1px solid var(--border-subtle);
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.form-tip {
  font-size: 12px;
  color: var(--text-tertiary);
}

@media (max-width: 767px) {
  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

/* 平板：表格内部横向滚动（页面级不溢出） */
@media (max-width: 1023px) {
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
