<script setup lang="ts">
/**
 * 菜单管理页 /system/menus（menu:manage）：
 * 平铺列表 → buildMenuTree 组装 el-table 树形表格 + 新建/编辑对话框（parentId 树选择、M/C/F 类型等）+ 删除
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import { getMenuList, createMenu, updateMenu, deleteMenu, type SysMenuVO, type MenuSaveParams, type MenuType } from '../api/menus'
import { buildMenuTree, type MenuTreeItem } from '../utils/menuTree'
import { formatDateTime } from '../utils/format'

const loading = ref(false)
const treeData = ref<MenuTreeItem[]>([])

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  parentId: 0,
  menuName: '',
  menuType: 'C' as MenuType,
  path: '',
  component: '',
  icon: '',
  sort: 1,
  visible: 1,
  permission: '',
})

/** 父菜单选择数据（顶级 + 仅目录/菜单节点可作为父级） */
const parentOptions = ref<MenuTreeItem[]>([])

function buildParentOptions() {
  // 顶级 + 目录(M) 与菜单(C) 节点；按钮(F) 不能作为父级
  const top: MenuTreeItem = {
    id: 0,
    parentId: -1,
    menuName: '顶级菜单',
    menuType: 'M',
    sort: 0,
    visible: 1,
    createTime: '',
    children: treeData.value,
  }
  const filter = (items: MenuTreeItem[]): MenuTreeItem[] =>
    items
      .filter((n) => n.menuType !== 'F')
      .map((n) => ({ ...n, children: filter(n.children ?? []) }))
  top.children = filter(treeData.value)
  parentOptions.value = [top]
}

function resetForm() {
  editingId.value = null
  form.parentId = 0
  form.menuName = ''
  form.menuType = 'C'
  form.path = ''
  form.component = ''
  form.icon = ''
  form.sort = 1
  form.visible = 1
  form.permission = ''
}

function openCreate() {
  resetForm()
  buildParentOptions()
  dialogVisible.value = true
}

function openEdit(row: SysMenuVO) {
  editingId.value = row.id
  form.parentId = row.parentId
  form.menuName = row.menuName
  form.menuType = row.menuType
  form.path = row.path ?? ''
  form.component = row.component ?? ''
  form.icon = row.icon ?? ''
  form.sort = row.sort ?? 1
  form.visible = row.visible
  form.permission = row.permission ?? ''
  buildParentOptions()
  dialogVisible.value = true
}

async function save() {
  if (!form.menuName.trim()) {
    ElMessage.warning('请输入菜单名称')
    return
  }
  saving.value = true
  try {
    const params: MenuSaveParams = {
      id: editingId.value ?? undefined,
      parentId: form.parentId,
      menuName: form.menuName.trim(),
      menuType: form.menuType,
      path: form.path.trim() || undefined,
      component: form.component.trim() || undefined,
      icon: form.icon.trim() || undefined,
      sort: form.sort,
      visible: form.visible,
      permission: form.permission.trim() || undefined,
    }
    if (editingId.value == null) {
      await createMenu(params)
      ElMessage.success('菜单创建成功')
    } else {
      await updateMenu(params)
      ElMessage.success('菜单更新成功')
    }
    dialogVisible.value = false
    loadMenus()
  } catch {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SysMenuVO) {
  try {
    await ElMessageBox.confirm(`确定删除菜单「${row.menuName}」吗？其子菜单将一并删除。`, '删除菜单', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteMenu(row.id)
    ElMessage.success('菜单已删除')
    loadMenus()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 查询 ============
async function loadMenus() {
  loading.value = true
  try {
    const flat = await getMenuList()
    treeData.value = buildMenuTree(flat)
  } catch {
    // 错误已提示
  } finally {
    loading.value = false
  }
}

/** 菜单类型中文标签 */
const TYPE_LABELS: Record<MenuType, string> = {
  M: '目录',
  C: '菜单',
  F: '按钮',
}

onMounted(loadMenus)
</script>

<template>
  <div class="manage-page">
    <section class="table-card">
      <div class="table-toolbar">
        <span class="toolbar-title">菜单管理</span>
        <el-button v-permission="['menu:manage']" type="primary" :icon="Plus" @click="openCreate">新建菜单</el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="treeData"
        row-key="id"
        :tree-props="{ children: 'children' }"
        default-expand-all
        class="manage-table"
      >
        <el-table-column prop="menuName" label="菜单名称" min-width="200" />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.menuType === 'F' ? 'info' : row.menuType === 'M' ? 'warning' : 'primary'" effect="light">
              {{ TYPE_LABELS[row.menuType as MenuType] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="path" label="路由路径" min-width="150">
          <template #default="{ row }">{{ row.path || '-' }}</template>
        </el-table-column>
        <el-table-column prop="permission" label="权限标识" min-width="150">
          <template #default="{ row }">{{ row.permission || '-' }}</template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="70" />
        <el-table-column label="可见" width="70">
          <template #default="{ row }">
            <el-tag :type="row.visible === 1 ? 'success' : 'info'" size="small" effect="plain">
              {{ row.visible === 1 ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="['menu:manage']" size="small" :icon="Edit" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['menu:manage']" size="small" :icon="Delete" text type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建菜单' : '编辑菜单'" width="520px">
      <el-form label-position="top" class="menu-form">
        <div class="form-row">
          <el-form-item label="上级菜单">
            <el-tree-select
              v-model="form.parentId"
              :data="parentOptions"
              :props="{ label: 'menuName', children: 'children' }"
              node-key="id"
              check-strictly
              :render-after-expand="false"
              style="width: 100%"
            />
          </el-form-item>
          <el-form-item label="菜单类型">
            <el-select v-model="form.menuType" style="width: 100%">
              <el-option label="目录（仅分组）" value="M" />
              <el-option label="菜单（页面）" value="C" />
              <el-option label="按钮（权限点）" value="F" />
            </el-select>
          </el-form-item>
        </div>

        <el-form-item label="菜单名称" required>
          <el-input v-model="form.menuName" maxlength="50" placeholder="如：工单列表" />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="路由路径">
            <el-input v-model="form.path" placeholder="如：/tickets（F 型留空）" />
          </el-form-item>
          <el-form-item label="组件">
            <el-input v-model="form.component" placeholder="如：TicketList（F 型留空）" />
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="图标">
            <el-input v-model="form.icon" placeholder="如：ticket" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sort" :min="0" :max="9999" style="width: 100%" />
          </el-form-item>
        </div>

        <div class="form-row">
          <el-form-item label="权限标识">
            <el-input v-model="form.permission" placeholder="如：ticket:view（C/F 型填写）" />
          </el-form-item>
          <el-form-item label="可见">
            <el-radio-group v-model="form.visible">
              <el-radio :value="1">显示</el-radio>
              <el-radio :value="0">隐藏</el-radio>
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

.toolbar-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.manage-table {
  --el-table-border-color: var(--border-subtle);
  --el-table-header-bg-color: var(--bg-subtle);
  --el-table-header-text-color: var(--text-secondary);
  --el-table-row-hover-bg-color: var(--accent-subtle);
  width: 100%;
}

.menu-form .form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

.menu-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

@media (max-width: 767px) {
  .menu-form .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}

/* 平板：表格内部横向滚动（树形表格列多，页面级不溢出） */
@media (max-width: 1023px) {
  .manage-table {
    min-width: 880px;
  }
}

/* 手机：对话框宽度适配 */
@media (max-width: 767px) {
  :deep(.el-dialog) {
    width: 92% !important;
  }
}
</style>
