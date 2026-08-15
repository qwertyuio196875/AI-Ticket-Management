<script setup lang="ts">
/**
 * 用户管理页 /system/users（user:manage）：
 * 表格 + 新建/编辑对话框（密码编辑时留空 = 不改）+ 分配角色对话框 + 删除确认
 */
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, UserFilled } from '@element-plus/icons-vue'
import {
  getUserList, createUser, updateUser, deleteUser, getUserRoles, assignUserRoles,
  type SysUserVO, type UserSaveParams,
} from '../api/users'
import { getAllRoles, type SysRoleVO } from '../api/roles'
import { formatDateTime } from '../utils/format'

const loading = ref(false)
const records = ref<SysUserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

// ============ 新建 / 编辑 ============
const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const saving = ref(false)
const form = reactive({
  username: '',
  password: '',
  nickname: '',
  status: 1,
})

function resetForm() {
  editingId.value = null
  form.username = ''
  form.password = ''
  form.nickname = ''
  form.status = 1
}

function openCreate() {
  resetForm()
  dialogVisible.value = true
}

function openEdit(row: SysUserVO) {
  editingId.value = row.id
  form.username = row.username
  form.password = ''
  form.nickname = row.nickname
  form.status = row.status
  dialogVisible.value = true
}

async function save() {
  if (!form.username.trim()) {
    ElMessage.warning('请输入用户名（3-50 字符）')
    return
  }
  if (editingId.value == null && !form.password.trim()) {
    ElMessage.warning('创建用户必须填写密码')
    return
  }
  saving.value = true
  try {
    const params: UserSaveParams = {
      id: editingId.value ?? undefined,
      username: form.username.trim(),
      nickname: form.nickname.trim() || undefined,
      password: form.password || undefined,
      status: form.status,
    }
    if (editingId.value == null) {
      await createUser(params)
      ElMessage.success('用户创建成功')
    } else {
      await updateUser(params)
      ElMessage.success('用户更新成功')
    }
    dialogVisible.value = false
    loadUsers()
  } catch {
    // 错误已提示
  } finally {
    saving.value = false
  }
}

async function handleDelete(row: SysUserVO) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '删除用户', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteUser(row.id)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 分配角色 ============
const roleDialogVisible = ref(false)
const roleTarget = ref<SysUserVO | null>(null)
const roleOptions = ref<SysRoleVO[]>([])
const roleIds = ref<number[]>([])
const roleSaving = ref(false)

async function openRoleDialog(row: SysUserVO) {
  roleTarget.value = row
  try {
    const [assigned, allRoles] = await Promise.all([getUserRoles(row.id), getAllRoles()])
    roleIds.value = assigned
    roleOptions.value = allRoles
    roleDialogVisible.value = true
  } catch {
    // 错误已提示
  }
}

async function saveRoles() {
  if (!roleTarget.value) return
  roleSaving.value = true
  try {
    await assignUserRoles(roleTarget.value.id, roleIds.value)
    ElMessage.success('角色分配成功')
    roleDialogVisible.value = false
  } catch {
    // 错误已提示
  } finally {
    roleSaving.value = false
  }
}

// ============ 查询 ============
async function loadUsers() {
  loading.value = true
  try {
    const page = await getUserList({
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
  loadUsers()
}

onMounted(loadUsers)
</script>

<template>
  <div class="manage-page">
    <section class="table-card">
      <div class="table-toolbar">
        <el-input
          v-model="keyword"
          placeholder="搜索用户名 / 昵称"
          clearable
          style="width: 220px"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <div class="toolbar-actions">
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button v-permission="['user:manage']" type="primary" :icon="Plus" @click="openCreate">新建用户</el-button>
        </div>
      </div>

      <el-table v-loading="loading" :data="records" class="manage-table">
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="nickname" label="昵称" min-width="120">
          <template #default="{ row }">{{ row.nickname || '-' }}</template>
        </el-table-column>
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
            <el-button v-permission="['user:manage']" size="small" :icon="UserFilled" text type="primary" @click="openRoleDialog(row)">分配角色</el-button>
            <el-button v-permission="['user:manage']" size="small" :icon="Edit" text @click="openEdit(row)">编辑</el-button>
            <el-button v-permission="['user:manage']" size="small" :icon="Delete" text type="danger" @click="handleDelete(row)">删除</el-button>
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
          @current-change="loadUsers"
        />
      </div>
    </section>

    <!-- 新建 / 编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="editingId == null ? '新建用户' : '编辑用户'" width="440px">
      <el-form label-position="top">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" maxlength="50" placeholder="3-50 字符" />
        </el-form-item>
        <el-form-item :label="editingId == null ? '密码' : '密码（留空则不修改）'" :required="editingId == null">
          <el-input v-model="form.password" type="password" show-password maxlength="100" placeholder="至少 6 位" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="form.nickname" maxlength="50" placeholder="可选" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <el-dialog v-model="roleDialogVisible" :title="`分配角色：${roleTarget?.username ?? ''}`" width="420px">
      <el-checkbox-group v-model="roleIds" class="role-checkbox-group">
        <el-checkbox v-for="role in roleOptions" :key="role.id" :value="role.id">
          {{ role.roleName }}（{{ role.roleKey }}）
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="roleDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="saveRoles">保存</el-button>
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

.role-checkbox-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 4px 0;
}

.role-checkbox-group :deep(.el-checkbox__label) {
  color: var(--text-primary);
  font-size: 13px;
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
