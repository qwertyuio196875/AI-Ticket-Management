<script setup lang="ts">
/**
 * 新建工单页 /tickets/create：
 * - 表单：标题（必填 1-100）/ 内容（必填 1-2000）/ 分类（下拉，可空——留空由 AI 自动分类）/ 优先级（字典，默认 MEDIUM）
 * - 提交 POST /v1/tickets → 返回 id → 查询详情 → 弹窗展示 AI 分类结果 → 确认跳详情页
 */
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createTicket, getTicket, type TicketVO } from '../api/tickets'
import { getDictsByType, type SysDictVO } from '../api/dicts'
import { getEnabledCategories, type TicketCategoryVO } from '../api/ticketCategories'
import { STATUS_LABELS, PRIORITY_LABELS, type TicketPriority } from '../utils/ticketState'

const router = useRouter()

const form = reactive({
  title: '',
  content: '',
  type: '',
  priority: 'MEDIUM' as TicketPriority,
})

const categoryOptions = ref<TicketCategoryVO[]>([])
const priorityOptions = ref<SysDictVO[]>([])

const submitting = ref(false)
const createdTicket = ref<TicketVO | null>(null)
const resultDialogVisible = ref(false)

onMounted(async () => {
  const [categories, priorities] = await Promise.all([
    getEnabledCategories().catch(() => []),
    getDictsByType('priority').catch(() => []),
  ])
  categoryOptions.value = categories
  priorityOptions.value = priorities
})

/** 提交：创建 → 拉详情（拿 AI 分类结果）→ 弹窗展示 */
async function handleSubmit() {
  if (!form.title.trim() || !form.content.trim()) {
    ElMessage.warning('请填写标题与内容')
    return
  }
  submitting.value = true
  try {
    const id = await createTicket({
      title: form.title.trim(),
      content: form.content.trim(),
      type: form.type || undefined,
      priority: form.priority,
    })
    const detail = await getTicket(id)
    createdTicket.value = detail
    resultDialogVisible.value = true
  } catch {
    // 错误已由 http 拦截器统一提示
  } finally {
    submitting.value = false
  }
}

/** 确认弹窗 → 跳转详情页 */
function handleConfirm() {
  resultDialogVisible.value = false
  if (createdTicket.value) {
    router.push(`/tickets/${createdTicket.value.id}`)
  }
}

/** AI 分类结果的中文标签（type 为分类名，直接展示；priority/status 走字典） */
const typeLabel = computed(() => createdTicket.value?.type || '待 AI 分类')
const priorityLabel = computed(() =>
  createdTicket.value?.priority ? PRIORITY_LABELS[createdTicket.value.priority] ?? createdTicket.value.priority : '-',
)
const statusLabel = computed(() =>
  createdTicket.value?.status ? STATUS_LABELS[createdTicket.value.status] ?? createdTicket.value.status : '-',
)
</script>

<template>
  <div class="ticket-create">
    <section class="form-card">
      <div class="form-header">
        <h2 class="form-title">新建工单</h2>
        <p class="form-desc">描述您遇到的问题，AI 将辅助完成分类与优先级识别</p>
      </div>

      <el-form label-position="top" class="create-form" @submit.prevent="handleSubmit">
        <el-form-item label="标题" required>
          <el-input
            v-model="form.title"
            placeholder="一句话概括问题（1-100 字）"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="问题描述" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="详细描述问题现象、出现时间、影响范围等（1-2000 字）"
            maxlength="2000"
            show-word-limit
          />
        </el-form-item>

        <div class="form-row">
          <el-form-item label="工单分类">
            <el-select v-model="form.type" placeholder="留空由 AI 自动分类" clearable style="width: 100%">
              <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.name" />
            </el-select>
            <div class="field-tip">留空由 AI 自动分类（2 秒内返回，超时降级为「待人工分配」）</div>
          </el-form-item>

          <el-form-item label="优先级">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option
                v-for="item in priorityOptions"
                :key="item.id"
                :label="item.dictLabel"
                :value="item.dictValue"
              />
            </el-select>
          </el-form-item>
        </div>

        <div class="form-actions">
          <el-button @click="router.back()">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">提交工单</el-button>
        </div>
      </el-form>
    </section>

    <!-- AI 分类结果弹窗 -->
    <el-dialog v-model="resultDialogVisible" title="工单创建成功" width="420px" :close-on-click-modal="false" :show-close="false">
      <div class="result-body">
        <div class="result-icon">
          <el-icon :size="22"><i class="el-icon-check" /></el-icon>
        </div>
        <p class="result-desc">AI 已完成工单分类，以下是识别结果：</p>
        <div class="result-grid">
          <div class="result-item">
            <span class="result-label">工单编号</span>
            <span class="result-value">{{ createdTicket?.ticketNo ?? '-' }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">AI 分类</span>
            <span class="result-value">{{ typeLabel }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">优先级</span>
            <span class="result-value">{{ priorityLabel }}</span>
          </div>
          <div class="result-item">
            <span class="result-label">当前状态</span>
            <span class="result-value">{{ statusLabel }}</span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button type="primary" @click="handleConfirm">查看工单详情</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-create {
  max-width: 720px;
  margin: 0 auto;
}

.form-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 28px 32px;
}

.form-header {
  margin-bottom: 24px;
}

.form-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
}

.form-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-secondary);
}

.create-form :deep(.el-form-item__label) {
  color: var(--text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.form-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

.field-tip {
  margin-top: 4px;
  font-size: 12px;
  color: var(--text-tertiary);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid var(--border-subtle);
}

/* AI 结果弹窗 */
.result-body {
  text-align: center;
}

.result-icon {
  width: 44px;
  height: 44px;
  margin: 0 auto 12px;
  border-radius: 50%;
  background: rgba(16, 124, 16, 0.12);
  color: #107c10;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-desc {
  font-size: 13px;
  color: var(--text-secondary);
  margin-bottom: 16px;
}

.result-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
  text-align: left;
}

.result-item {
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.result-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.result-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-primary);
}

/* 窄屏：表单行堆叠 */
@media (max-width: 767px) {
  .form-row {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
</style>
