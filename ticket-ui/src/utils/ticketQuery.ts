/**
 * 工单列表查询参数构造（纯函数）：
 * 把筛选表单 + 分页信息归一化为后端 TicketQueryDTO 对应的 query 参数对象，
 * 空值（'' / null / undefined / 空数组）一律剔除，日期范围拆分为 dateFrom / dateTo。
 * 注意：后端 ticket_info.type 存的是分类名称（ticket_category.name），故 type 筛选值为分类名。
 */

/** 后端 GET /v1/tickets 的 query 参数 */
export interface TicketListParams {
  pageNum: number
  pageSize: number
  status?: string
  priority?: string
  type?: string
  handlerId?: number
  dateFrom?: string
  dateTo?: string
}

/** 列表页筛选表单（el-form 绑定结构） */
export interface TicketFilterForm {
  status?: string
  priority?: string
  type?: string
  handlerId?: number | null
  dateRange?: [string, string] | null
}

/** 归一化筛选表单 + 分页 → 后端 query 参数 */
export function buildTicketQueryParams(
  form: TicketFilterForm,
  pageNum: number,
  pageSize: number,
): TicketListParams {
  const params: TicketListParams = { pageNum, pageSize }
  if (form.status) params.status = form.status
  if (form.priority) params.priority = form.priority
  if (form.type?.trim()) params.type = form.type.trim()
  if (form.handlerId != null) params.handlerId = form.handlerId
  if (form.dateRange?.length === 2) {
    params.dateFrom = form.dateRange[0]
    params.dateTo = form.dateRange[1]
  }
  return params
}
