import http from '../utils/http'

/** 工单分类展示对象 */
export interface TicketCategoryVO {
  id: number
  name: string
  description: string
  sort: number
  status: number
  createTime: string
}

/** 工单分类保存请求体 */
export interface TicketCategorySaveParams {
  id?: number
  name: string
  description?: string
  sort?: number
  status: number
}

/** 查询所有已启用分类（下拉用，登录即可） */
export function getEnabledCategories(): Promise<TicketCategoryVO[]> {
  return http.get<TicketCategoryVO[]>('/v1/ticket-categories').then((res) => res.data)
}

/** 查询全部分类（含禁用，分类管理页用） */
export function getManageCategories(): Promise<TicketCategoryVO[]> {
  return http.get<TicketCategoryVO[]>('/v1/ticket-categories/manage').then((res) => res.data)
}

/** 创建分类 → 返回 id */
export function createCategory(params: TicketCategorySaveParams): Promise<number> {
  return http.post<number>('/v1/ticket-categories', params).then((res) => res.data)
}

/** 更新分类 */
export function updateCategory(params: TicketCategorySaveParams): Promise<void> {
  return http.put<void>('/v1/ticket-categories', params).then(() => undefined)
}

/** 删除分类 */
export function deleteCategory(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/ticket-categories/${id}`).then(() => undefined)
}
