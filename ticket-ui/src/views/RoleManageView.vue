<script setup lang="ts">
/**
 * 角色管理页 /system/roles（role:manage）：
 * 表格 + 新建/编辑对话框 + 菜单分配树对话框（GET /menus 平铺 → buildMenuTree → el-tree 回显/保存）+ 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Menu as MenuIcon } from '@element-plus/icons-vue'
import {
  getRoleList, createRole, updateRole, deleteRole, getRoleMenus, assignRoleMenus,
  type SysRoleVO, type RoleSaveParams,
} from '../api/roles'
import { getMenuList } from '../api/menus'
import { buildMenuTree, type MenuTreeItem } from '../utils/menuTree'
import { formatDateTime } from '../utils/format'
import type { ElTree } from 'element-plus'

const loading = ref(false)
const records = ref<SysRoleVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  roleName: '',
  roleKey: '',
  remark: '',
})

function resetForm() {
  editingId.value = null
  form.roleName = ''
  form.roleKey = ''
  form.remark = ''
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: SysRoleVO) {
  editingId.value = row.id
  form.roleName = row.roleName
  form.roleKey = row.roleKey
  form.remark = row.remark
  dialogVisible.value = true
}

async function save() {
  if (!form.roleName.trim() || !form.roleKey.trim()) {
    ElMessage.warning('角色名称与标识不能为空')
    return
  }
  saving.value = true
  try {
    const params: RoleSaveParams = {
      id: editingId.value ?? undefined,
      roleName: form.roleName.trim(),
      roleKey: form.roleKey.trim(),
      remark: form.remark.trim() || undefined,
    }
    if (editingId.value == null) {
      await createRole(params)
      ElMessage.success('角色创建成功')
    } else {
      await updateRole(params)
      ElMessage.success('角色更新成功')
    }
    dialogVisible.value = false
    loadRoles()
  } catch {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SysRoleVO) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.roleName}」吗？`, '删除角色', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteRole(row.id)
    ElMessage.success('角色已删除')
    loadRoles()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 菜单分配树 ============
const menuDialogVisible = ref(false)
const menuTarget = ref<SysRoleVO | null>(null)
const menuTree = ref<MenuTreeItem[]>([])
const checkedMenuKeys = ref<number[]>([])
const menuTreeRef = ref<InstanceType<typeof ElTree>>()
const menuSaving = ref(false)

async function openMenuDialog(row: SysRoleVO) {
  menuTarget.value = row
  try {
    const [flat, assigned] = await Promise.all([getMenuList(), getRoleMenus(row.id)])
    menuTree.value = buildMenuTree(flat)
    checkedMenuKeys.value = assigned
    menuDialogVisible.value = true
  } catch {
    // 错误已提示
  }
}

/** 展开全部节点，方便勾选深层按钮权限 */
function expandAll() {
  const tree = menuTreeRef.value
  if (!tree) return
  Object.values(tree.store.nodesMap).forEach((node) => (node.expanded = true))
}

async function saveMenus() {
  if (!menuTarget.value) return
  const checked = (menuTreeRef.value?.getCheckedKeys() ?? []) as Array<number | string>
  const halfChecked = (menuTreeRef.value?.getHalfCheckedKeys() ?? []) as Array<number | string>
  const menuIds = [...checked, ...halfChecked].map(Number)
  menuSaving.value = true
  try {
    await assignRoleMenus(menuTarget.value.id, menuIds)
    ElMessage.success('菜单权限已更新')
    menuDialogVisible.value = false
  } catch {
    // 错误已提示
  } finally {
    menuSaving.value = false
  }
}

// ============ 查询 ============
async function loadRoles() {
  loading.value = true
  try {
    const page = await getRoleList({
      keyword: keyword.value.trim() || undefined,
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

function handleSearch() {
  pageNum.value = 1
  loadRoles()
}

onMounted(loadRoles)
</script>

<template>
  <div class="manage-page">
    <section class="table-card">
      <div class="table-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索角色名称 / 标识"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <div class="toolbar-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button v-permission="['role:manage']" type="primary" :icon="Plus" @click="openCreate">新建角色</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" class="manage-table">
        <el-table-column prop="roleName" label="角色名称" min-width="140" />
        <el-table-column prop="roleKey" label="角色标识" min-width="120">
          <template #default="{ row }">
            <el-tag size="small" effect="plain">{{ row.roleKey }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['role:manage']" size="small" :icon="MenuIcon" text type="primary" @click="openMenuDialog(row)">分配菜单</el-button>
            <el-button v-permission="['role:manage']" size="small" :icon="Edit" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['role:manage']" size="small" :icon="Delete" text type="danger" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadRoles"
        />
      </div>
    </section>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建角色' : '编辑角色'" width="440px">
      <el-form label-position="top">
        <el-form-item label="角色名称" required>
          <el-input v-model="form.roleName" maxlength="50" placeholder="如：客服坐席" />
        </el-form-item>
        <el-form-item label="角色标识" required>
          <el-input v-model="form.roleKey" maxlength="50" placeholder="如：agent（英文小写）" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 菜单分配对话框 -->
    <el-dialog v-model="menuDialogVisible" :title="`分配菜单：${menuTarget?.roleName ?? ''}`" width="480px">
      <div class="menu-tree-toolbar">
        <el-button size="small" text type="primary" @click="expandAll">展开全部</el-button>
      </div>
      <el-tree
        :key="menuTarget?.id"
        ref="menuTreeRef"
        :data="menuTree"
        show-checkbox
        node-key="id"
        default-expand-all
        :default-checked-keys="checkedMenuKeys"
        :props="{ label: 'menuName', children: 'children' }"
        class="menu-tree"
      >
        <template #default="{ data }">
          <span class="menu-tree-node">
            {{ data.menuName }}
            <el-tag v-if="data.menuType === 'F'" size="small" effect="plain" class="menu-type-tag">按钮</el-tag>
            <el-tag v-else-if="data.menuType === 'M'" size="small" effect="plain" class="menu-type-tag">目录</el-tag>
            <el-tag v-else size="small" effect="plain" class="menu-type-tag">菜单</el-tag>
          </span>
        </template>
      </el-tree>
      <template #footer>
        <el-button @click="menuDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="menuSaving" @click="saveMenus">保存</el-button>
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

.toolbar-actions {
  display: flex;
  gap: 8px;
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

.menu-tree-toolbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 8px;
}

.menu-tree {
  --el-tree-node-hover-bg-color: var(--bg-subtle);
  max-height: 420px;
  overflow-y: auto;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 8px;
}

.menu-tree-node {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.menu-type-tag {
  font-size: 10px;
  height: 18px;
  line-height: 18px;
  padding: 0 6px;
}

/* 平板与手机：工具条换行、表格内部横向滚动（页面级不溢出） */
@media (max-width: 1023px) {
  .table-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-actions {
    justify-content: flex-end;
  }

  /* 列保底宽度：窄屏时 el-table 在容器内横向滚动 */
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
