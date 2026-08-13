<script setup lang="ts">
/**
 * 工单详情页 /tickets/:id —— 三列布局：
 * - 左列：信息卡 + 描述 + 附件（上传/下载/删除）+ 编辑标题内容
 * - 中列：时间线（ticket_log，eventType → 图标 + 中文标签）
 * - 右列：评论线程（parentId 组装回复树）+ AI 智能回复 + 分配 + 状态流转/关闭
 * 响应式：≥1200 三列；768-1199 两列；<768 单列
 */
import { computed, onMounted, reactive, ref, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Plus, Edit, Refresh, UserFilled, ChatDotRound, Cpu, UploadFilled, Delete, Download,
  MagicStick, Back,
} from '@element-plus/icons-vue'
import {
  getTicket, getTicketLogs, getComments, getAttachments, createComment, deleteComment,
  getAiReply, uploadAttachment, deleteAttachment, updateTicket, changeTicketStatus,
  assignTicket, closeTicket,
  type TicketVO, type TicketCommentVO, type TicketLogVO, type TicketAttachmentVO,
  type TicketAiReplyVO, type TicketCommentCreateParams,
} from '../api/tickets'
import { getUserList, type SysUserVO } from '../api/users'
import { useAuthStore } from '../stores/auth'
import { buildCommentTree, type CommentNode } from '../utils/commentTree'
import { EVENT_LABELS, STATUS_LABELS, STATUS_TAG_TYPES, PRIORITY_LABELS, PRIORITY_TAG_TYPES, COMMENT_TYPE_LABELS, canTransitTo, nextStatuses, type CommentType, type TicketStatus } from '../utils/ticketState'
import { formatDateTime, formatFileSize } from '../utils/format'
import type { UploadRequestOptions } from 'element-plus'

const route = useRoute()
const router = useRouter()
const store = useAuthStore()

const ticketId = computed(() => String(route.params.id))

// ============ 详情数据 ============
const ticket = ref<TicketVO | null>(null)
const logs = ref<TicketLogVO[]>([])
const comments = ref<TicketCommentVO[]>([])
const attachments = ref<TicketAttachmentVO[]>([])
const loading = ref(true)
const userOptions = ref<SysUserVO[]>([])

/** 事件类型 → 图标组件映射 */
const eventIconMap: Record<string, Component> = {
  CREATED: Plus,
  UPDATED: Edit,
  STATUS_CHANGED: Refresh,
  ASSIGNED: UserFilled,
  COMMENTED: ChatDotRound,
  AI_CALLED: Cpu,
}

function eventIcon(eventType: string): Component {
  return eventIconMap[eventType] ?? Plus
}

async function loadDetail() {
  loading.value = true
  try {
    const [detail, logList, commentList, attachmentList] = await Promise.all([
      getTicket(ticketId.value),
      getTicketLogs(ticketId.value),
      getComments(ticketId.value),
      getAttachments(ticketId.value),
    ])
    ticket.value = detail
    logs.value = logList
    comments.value = commentList
    attachments.value = attachmentList
  } catch {
    // 错误已由 http 拦截器提示；详情加载失败时保留旧数据
  } finally {
    loading.value = false
  }
}

/** 仅刷新日志（状态变更 / 评论 / AI 调用后时间线变化） */
async function refreshLogs() {
  try {
    logs.value = await getTicketLogs(ticketId.value)
  } catch {
    // 忽略
  }
}

/** 仅刷新评论树 */
async function refreshComments() {
  try {
    comments.value = await getComments(ticketId.value)
  } catch {
    // 忽略
  }
}

// ============ 用户映射（处理人 / 创建人展示与分配下拉） ============
const canManageUsers = computed(() => store.hasAnyPermission(['user:manage']))
const userMap = computed(() => new Map(userOptions.value.map((u) => [u.id, u.nickname || u.username])))

function userName(id: number | null | undefined): string {
  if (id == null) return '-'
  return userMap.value.get(id) ?? `#${id}`
}

// ============ 编辑工单（ticket:view，后端判创建人/管理员） ============
const editVisible = ref(false)
const editForm = reactive({ title: '', content: '' })
const savingEdit = ref(false)

function openEdit() {
  if (!ticket.value) return
  editForm.title = ticket.value.title
  editForm.content = ticket.value.content
  editVisible.value = true
}

async function saveEdit() {
  if (!editForm.title.trim() || !editForm.content.trim()) {
    ElMessage.warning('标题与内容不能为空')
    return
  }
  savingEdit.value = true
  try {
    await updateTicket(ticketId.value, { title: editForm.title.trim(), content: editForm.content.trim() })
    ElMessage.success('工单已更新')
    editVisible.value = false
    await loadDetail()
    await refreshLogs()
  } catch {
    // 错误已提示
  } finally {
    savingEdit.value = false
  }
}

// ============ 附件（ticket:upload） ============
const uploading = ref(false)

async function handleUpload(options: UploadRequestOptions) {
  uploading.value = true
  try {
    await uploadAttachment(ticketId.value, options.file as File)
    ElMessage.success('附件上传成功')
    attachments.value = await getAttachments(ticketId.value)
  } catch {
    // 错误已提示
  } finally {
    uploading.value = false
  }
}

async function handleDeleteAttachment(attachment: TicketAttachmentVO) {
  try {
    await ElMessageBox.confirm(`确定删除附件「${attachment.fileName}」吗？`, '删除附件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteAttachment(ticketId.value, attachment.id)
    ElMessage.success('附件已删除')
    attachments.value = await getAttachments(ticketId.value)
    await refreshLogs()
  } catch {
    // 取消或失败均忽略
  }
}

function downloadAttachment(attachment: TicketAttachmentVO) {
  if (attachment.downloadUrl) {
    window.open(attachment.downloadUrl, '_blank')
  } else {
    ElMessage.info('该附件暂无可下载链接')
  }
}

// ============ 评论（ticket:comment） ============
const commentText = ref('')
const commentType = ref<CommentType>('AGENT')
const replyTarget = ref<CommentNode | null>(null)
const sendingComment = ref(false)

const commentTree = computed(() => buildCommentTree(comments.value))

function startReply(comment: CommentNode) {
  replyTarget.value = comment
  commentText.value = ''
}

function cancelReply() {
  replyTarget.value = null
}

async function sendComment() {
  if (!commentText.value.trim()) {
    ElMessage.warning('请输入评论内容')
    return
  }
  const payload: TicketCommentCreateParams = {
    content: commentText.value.trim(),
    commentType: commentType.value,
  }
  if (replyTarget.value) payload.parentId = replyTarget.value.id

  sendingComment.value = true
  try {
    await createComment(ticketId.value, payload)
    ElMessage.success('评论已发送')
    commentText.value = ''
    replyTarget.value = null
    await refreshComments()
    await refreshLogs()
  } catch {
    // 错误已提示
  } finally {
    sendingComment.value = false
  }
}

async function handleDeleteComment(comment: TicketCommentVO) {
  try {
    await ElMessageBox.confirm('确定删除这条评论吗？', '删除评论', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
    await deleteComment(ticketId.value, comment.id)
    ElMessage.success('评论已删除')
    await refreshComments()
    await refreshLogs()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ AI 智能回复（ai:invoke） ============
const aiLoading = ref(false)
const aiReply = ref<TicketAiReplyVO | null>(null)
const aiDialogVisible = ref(false)

async function handleAiReply() {
  aiLoading.value = true
  try {
    aiReply.value = await getAiReply(ticketId.value)
    aiDialogVisible.value = true
    await refreshLogs()
  } catch {
    // 错误已提示
  } finally {
    aiLoading.value = false
  }
}

// ============ 分配处理人（ticket:assign） ============
const assignVisible = ref(false)
const assignForm = reactive({ handlerId: undefined as number | undefined, reason: '' })
const assigning = ref(false)

function openAssign() {
  if (!ticket.value) return
  assignForm.handlerId = ticket.value.handlerId ?? undefined
  assignForm.reason = ''
  assignVisible.value = true
}

async function saveAssign() {
  if (!assignForm.handlerId) {
    ElMessage.warning('请选择处理人')
    return
  }
  assigning.value = true
  try {
    await assignTicket(ticketId.value, {
      handlerId: assignForm.handlerId,
      reason: assignForm.reason.trim() || undefined,
    })
    ElMessage.success('工单已分配')
    assignVisible.value = false
    await loadDetail()
    await refreshLogs()
  } catch {
    // 错误已提示
  } finally {
    assigning.value = false
  }
}

// ============ 状态流转（ticket:update）+ 关闭（ticket:close） ============
const statusVisible = ref(false)
const statusForm = reactive({ targetStatus: undefined as TicketStatus | undefined, reason: '' })
const changingStatus = ref(false)

/** 可流转目标：canTransitTo 表中除 CLOSED 外的目标（CLOSED 走独立关闭按钮） */
const transitTargets = computed<TicketStatus[]>(() => {
  if (!ticket.value) return []
  return nextStatuses(ticket.value.status).filter((s) => s !== 'CLOSED')
})

/** 是否可关闭：当前状态的合法迁移包含 CLOSED（即 CLOSED 状态下不显示关闭按钮） */
const canClose = computed(() => {
  if (!ticket.value) return false
  return canTransitTo(ticket.value.status, 'CLOSED')
})

function openStatusDialog(target: TicketStatus) {
  statusForm.targetStatus = target
  statusForm.reason = ''
  statusVisible.value = true
}

async function saveStatus() {
  if (!statusForm.targetStatus) return
  changingStatus.value = true
  try {
    await changeTicketStatus(ticketId.value, {
      targetStatus: statusForm.targetStatus,
      reason: statusForm.reason.trim() || undefined,
    })
    ElMessage.success('状态已更新')
    statusVisible.value = false
    await loadDetail()
    await refreshLogs()
  } catch {
    // 错误已提示
  } finally {
    changingStatus.value = false
  }
}

async function handleClose() {
  try {
    await ElMessageBox.confirm('关闭后工单将不可再流转，确定关闭吗？', '关闭工单', {
      type: 'warning',
      confirmButtonText: '关闭',
      cancelButtonText: '取消',
    })
    await closeTicket(ticketId.value)
    ElMessage.success('工单已关闭')
    await loadDetail()
    await refreshLogs()
  } catch {
    // 取消或失败均忽略
  }
}

// ============ 加载用户（分配下拉 / 名字映射，仅 user:manage） ============
async function loadUsers() {
  if (!canManageUsers.value) return
  try {
    const page = await getUserList({ pageNum: 1, pageSize: 50 })
    userOptions.value = page.records
  } catch {
    // 忽略
  }
}

onMounted(() => {
  loadUsers()
  loadDetail()
})
</script>

<template>
  <div class="ticket-detail" v-loading="loading">
    <!-- 顶栏：返回 + 标题 + 状态 -->
    <div class="detail-header">
      <el-button :icon="Back" text @click="router.back()">返回</el-button>
      <div class="detail-header-main">
        <h2 class="detail-title">{{ ticket?.title ?? '工单详情' }}</h2>
        <span v-if="ticket" class="detail-no">{{ ticket.ticketNo }}</span>
      </div>
      <el-tag v-if="ticket" :type="STATUS_TAG_TYPES[ticket.status]" size="large" effect="light">
        {{ STATUS_LABELS[ticket.status] }}
      </el-tag>
    </div>

    <div class="detail-grid" v-if="ticket">
      <!-- ============ 左列：信息 + 描述 + 附件 ============ -->
      <section class="col-left">
        <div class="card">
          <div class="card-header">
            <span class="card-title">工单信息</span>
            <el-button v-permission="['ticket:view']" size="small" :icon="Edit" text @click="openEdit">
              编辑
            </el-button>
          </div>
          <div class="info-grid">
            <div class="info-item"><span class="info-label">分类</span><span class="info-value">{{ ticket.type || '待分类' }}</span></div>
            <div class="info-item"><span class="info-label">优先级</span>
              <el-tag :type="PRIORITY_TAG_TYPES[ticket.priority]" size="small" effect="light">
                {{ PRIORITY_LABELS[ticket.priority] ?? ticket.priority }}
              </el-tag>
            </div>
            <div class="info-item"><span class="info-label">创建人</span><span class="info-value">{{ userName(ticket.creatorId) }}</span></div>
            <div class="info-item"><span class="info-label">处理人</span><span class="info-value">{{ userName(ticket.handlerId) }}</span></div>
            <div class="info-item"><span class="info-label">创建时间</span><span class="info-value">{{ formatDateTime(ticket.createTime) }}</span></div>
            <div class="info-item"><span class="info-label">更新时间</span><span class="info-value">{{ formatDateTime(ticket.updateTime) }}</span></div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <span class="card-title">问题描述</span>
          </div>
          <p class="ticket-content">{{ ticket.content }}</p>
        </div>

        <div class="card">
          <div class="card-header">
            <span class="card-title">附件（{{ attachments.length }}）</span>
            <el-upload
              v-permission="['ticket:upload']"
              :show-file-list="false"
              :http-request="handleUpload"
              :disabled="uploading"
            >
              <el-button size="small" :icon="UploadFilled" :loading="uploading">上传附件</el-button>
            </el-upload>
          </div>

          <div v-if="attachments.length === 0" class="empty-tip">暂无附件</div>
          <ul v-else class="attachment-list">
            <li v-for="att in attachments" :key="att.id" class="attachment-item">
              <span class="attachment-name" :title="att.fileName">{{ att.fileName }}</span>
              <span class="attachment-size">{{ formatFileSize(att.size) }}</span>
              <div class="attachment-actions">
                <el-button v-permission="['ticket:view']" size="small" :icon="Download" text @click="downloadAttachment(att)">下载</el-button>
                <el-button
                  v-permission="['ticket:upload']"
                  size="small"
                  :icon="Delete"
                  text
                  type="danger"
                  @click="handleDeleteAttachment(att)"
                >
                  删除
                </el-button>
              </div>
            </li>
          </ul>
        </div>
      </section>

      <!-- ============ 中列：时间线 ============ -->
      <section class="col-center">
        <div class="card">
          <div class="card-header">
            <span class="card-title">处理记录</span>
          </div>

          <el-timeline v-if="logs.length > 0" class="log-timeline">
            <el-timeline-item
              v-for="log in logs"
              :key="log.id"
              :timestamp="formatDateTime(log.createTime)"
              placement="top"
            >
              <div class="log-entry">
                <div class="log-entry-head">
                  <el-icon :size="14" class="log-icon"><component :is="eventIcon(log.eventType)" /></el-icon>
                  <span class="log-title">{{ EVENT_LABELS[log.eventType as keyof typeof EVENT_LABELS] ?? log.eventType }}</span>
                  <span v-if="log.operatorName" class="log-operator">{{ log.operatorName }}</span>
                </div>
                <div class="log-content">{{ log.content }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
          <div v-else class="empty-tip">暂无操作记录</div>
        </div>
      </section>

      <!-- ============ 右列：评论 + AI + 分配 + 状态 ============ -->
      <section class="col-right">
        <div class="card">
          <div class="card-header">
            <span class="card-title">评论（{{ comments.length }}）</span>
          </div>

          <!-- 评论输入 -->
          <div class="comment-editor" v-permission="['ticket:comment']">
            <div v-if="replyTarget" class="reply-banner">
              回复 {{ replyTarget.creatorName || `#${replyTarget.creatorId}` }}：
              <el-button size="small" text type="primary" @click="cancelReply">取消</el-button>
            </div>
            <el-input
              v-model="commentText"
              type="textarea"
              :rows="3"
              maxlength="2000"
              placeholder="输入评论内容（1-2000 字）"
            />
            <div class="comment-editor-foot">
              <el-select v-model="commentType" size="small" style="width: 130px">
                <el-option label="客服" value="AGENT" />
                <el-option label="客户" value="CUSTOMER" />
                <el-option label="内部备注（仅内部可见）" value="INTERNAL" />
              </el-select>
              <el-button size="small" type="primary" :loading="sendingComment" @click="sendComment">
                发送评论
              </el-button>
            </div>
          </div>

          <!-- 评论树 -->
          <div v-if="commentTree.length === 0" class="empty-tip">暂无评论</div>
          <div v-else class="comment-list">
            <div v-for="node in commentTree" :key="node.id" class="comment-node">
              <div class="comment-item">
                <div class="comment-head">
                  <span class="comment-author">{{ node.creatorName || `#${node.creatorId}` }}</span>
                  <el-tag size="small" effect="plain" class="comment-type-tag">
                    {{ COMMENT_TYPE_LABELS[node.commentType] }}
                  </el-tag>
                  <span class="comment-time">{{ formatDateTime(node.createTime) }}</span>
                </div>
                <p class="comment-content">{{ node.content }}</p>
                <div class="comment-actions">
                  <el-button v-permission="['ticket:comment']" size="small" text type="primary" @click="startReply(node)">回复</el-button>
                  <el-button v-permission="['ticket:comment']" size="small" text type="danger" @click="handleDeleteComment(node)">删除</el-button>
                </div>
              </div>

              <!-- 回复子节点 -->
              <div v-for="child in node.children" :key="child.id" class="comment-node comment-child">
                <div class="comment-item">
                  <div class="comment-head">
                    <span class="comment-author">{{ child.creatorName || `#${child.creatorId}` }}</span>
                    <el-tag size="small" effect="plain" class="comment-type-tag">
                      {{ COMMENT_TYPE_LABELS[child.commentType] }}
                    </el-tag>
                    <span class="comment-time">{{ formatDateTime(child.createTime) }}</span>
                  </div>
                  <p class="comment-content">{{ child.content }}</p>
                  <div class="comment-actions">
                    <el-button v-permission="['ticket:comment']" size="small" text type="primary" @click="startReply(child)">回复</el-button>
                    <el-button v-permission="['ticket:comment']" size="small" text type="danger" @click="handleDeleteComment(child)">删除</el-button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- AI 智能回复 -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">AI 助手</span>
          </div>
          <p class="card-desc">基于工单内容与历史对话，AI 生成排查思路与解决方案建议。</p>
          <el-button
            v-permission="['ai:invoke']"
            type="primary"
            :icon="MagicStick"
            :loading="aiLoading"
            class="ai-btn"
            @click="handleAiReply"
          >
            AI 智能回复
          </el-button>
        </div>

        <!-- 分配与状态 -->
        <div class="card">
          <div class="card-header">
            <span class="card-title">工单操作</span>
          </div>

          <div class="ops-group">
            <el-button v-permission="['ticket:assign']" :icon="UserFilled" @click="openAssign">分配处理人</el-button>
          </div>

          <div v-if="transitTargets.length > 0" class="ops-group">
            <span class="ops-label">状态流转</span>
            <div class="ops-buttons">
              <el-button
                v-for="target in transitTargets"
                v-permission="['ticket:update']"
                :key="target"
                @click="openStatusDialog(target)"
              >
                → {{ STATUS_LABELS[target] }}
              </el-button>
            </div>
          </div>

          <div v-if="canClose" class="ops-group">
            <el-button v-permission="['ticket:close']" type="danger" plain @click="handleClose">关闭工单</el-button>
          </div>
        </div>
      </section>
    </div>

    <!-- ============ 弹窗们 ============ -->
    <!-- 编辑工单 -->
    <el-dialog v-model="editVisible" title="编辑工单" width="520px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="6" maxlength="2000" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingEdit" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 回复结果 -->
    <el-dialog v-model="aiDialogVisible" title="AI 智能回复" width="520px">
      <el-alert
        v-if="aiReply?.fallback"
        title="AI 暂不可用，已返回模板建议"
        type="warning"
        :closable="false"
        class="ai-alert"
      />
      <div class="ai-reply-body">{{ aiReply?.reply }}</div>
      <template #footer>
        <el-button type="primary" @click="aiDialogVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 分配处理人 -->
    <el-dialog v-model="assignVisible" title="分配处理人" width="420px">
      <el-form label-position="top">
        <el-form-item label="处理人" required>
          <el-select v-model="assignForm.handlerId" placeholder="选择处理人" filterable style="width: 100%">
            <el-option
              v-for="u in userOptions"
              :key="u.id"
              :label="u.nickname || u.username"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="分配说明">
          <el-input v-model="assignForm.reason" maxlength="255" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="saveAssign">确认分配</el-button>
      </template>
    </el-dialog>

    <!-- 状态流转 -->
    <el-dialog v-model="statusVisible" title="状态流转" width="420px">
      <el-form label-position="top">
        <el-form-item label="目标状态">
          <el-tag v-if="statusForm.targetStatus" :type="STATUS_TAG_TYPES[statusForm.targetStatus]" effect="light">
            {{ STATUS_LABELS[statusForm.targetStatus] }}
          </el-tag>
        </el-form-item>
        <el-form-item label="变更说明">
          <el-input v-model="statusForm.reason" maxlength="255" placeholder="可选（≤255 字）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="statusVisible = false">取消</el-button>
        <el-button type="primary" :loading="changingStatus" @click="saveStatus">确认变更</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.ticket-detail {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* ============ 顶栏 ============ */
.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 14px 20px;
}

.detail-header-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.detail-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.detail-no {
  font-size: 12px;
  color: var(--text-tertiary);
  font-family: 'Cascadia Mono', Consolas, monospace;
  flex-shrink: 0;
}

/* ============ 三列布局 ============ */
.detail-grid {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 16px;
  align-items: start;
}

.col-left,
.col-center,
.col-right {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

/* ============ 通用卡片 ============ */
.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-md);
  padding: 16px 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
}

.card-desc {
  font-size: 12px;
  color: var(--text-tertiary);
  line-height: 1.6;
  margin-bottom: 12px;
}

/* ============ 左列：信息卡 ============ */
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.info-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.info-value {
  font-size: 13px;
  color: var(--text-primary);
}

.ticket-content {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}

/* ============ 左列：附件 ============ */
.empty-tip {
  padding: 20px 0;
  text-align: center;
  font-size: 12px;
  color: var(--text-tertiary);
}

.attachment-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.attachment-item {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  padding: 8px 12px;
}

.attachment-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.attachment-size {
  font-size: 12px;
  color: var(--text-tertiary);
  flex-shrink: 0;
}

.attachment-actions {
  display: flex;
  flex-shrink: 0;
}

/* ============ 中列：时间线 ============ */
.log-timeline {
  padding-left: 4px;
}

.log-entry-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.log-icon {
  color: var(--accent);
}

.log-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.log-operator {
  font-size: 12px;
  color: var(--text-tertiary);
  margin-left: auto;
}

.log-content {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.6;
  word-break: break-word;
}

/* ============ 右列：评论 ============ */
.comment-editor {
  margin-bottom: 16px;
}

.reply-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--accent-subtle);
  border-radius: var(--radius-sm);
  padding: 6px 12px;
  margin-bottom: 8px;
  font-size: 12px;
  color: var(--accent);
}

.comment-editor-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
}

.comment-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.comment-node {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.comment-item {
  background: var(--bg-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 12px;
}

.comment-child {
  margin-left: 20px;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.comment-author {
  font-size: 13px;
  font-weight: 500;
  color: var(--text-primary);
}

.comment-type-tag {
  font-size: 11px;
}

.comment-time {
  font-size: 11px;
  color: var(--text-tertiary);
  margin-left: auto;
}

.comment-content {
  margin-top: 6px;
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
  word-break: break-word;
  white-space: pre-wrap;
}

.comment-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 2px;
}

/* ============ 右列：AI + 操作 ============ */
.ai-btn {
  width: 100%;
}

.ops-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 0;
  border-bottom: 1px solid var(--border-subtle);
}

.ops-group:last-child {
  border-bottom: none;
}

.ops-label {
  font-size: 12px;
  color: var(--text-tertiary);
}

.ops-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.ai-alert {
  margin-bottom: 14px;
}

.ai-reply-body {
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 320px;
  overflow-y: auto;
}

/* ============ 响应式 ============ */
/* 平板（768-1199）：左列 + 中列并排，右列换行占满 */
@media (max-width: 1199px) and (min-width: 768px) {
  .detail-grid {
    grid-template-columns: 1fr 1fr;
  }

  .col-right {
    grid-column: 1 / -1;
    display: grid;
    grid-template-columns: 1fr 1fr;
    align-items: start;
  }
}

/* 手机（<768）：单列堆叠 */
@media (max-width: 767px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .detail-header {
    flex-wrap: wrap;
  }
}
</style>
