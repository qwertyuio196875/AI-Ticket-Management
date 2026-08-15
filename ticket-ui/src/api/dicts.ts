import http from '../utils/http'
import type { PageVO } from './tickets'

/** 字典分页查询参数 */
export interface DictListParams {
  dictType?: string
  pageNum: number
  pageSize: number
}

/** 字典条目展示对象 */
export interface SysDictVO {
  id: number
  dictType: string
  dictValue: string
  dictLabel: string
  sort: number
  status: number
  remark: string
  createTime: string
}

/** 字典保存请求体（更新时 dictType / dictValue 不可改，由后端校验） */
export interface DictSaveParams {
  id?: number
  dictType: string
  dictValue: string
  dictLabel: string
  sort?: number
  status: number
  remark?: string
}

/** 分页查询字典（管理页用） */
export function getDictList(params: DictListParams): Promise<PageVO<SysDictVO>> {
  return http.get<PageVO<SysDictVO>>('/v1/dicts', { params }).then((res) => res.data)
}

/** 按类型查字典列表（下拉用，登录即可） */
export function getDictsByType(dictType: string): Promise<SysDictVO[]> {
  return http.get<SysDictVO[]>(`/v1/dicts/type/${dictType}`).then((res) => res.data)
}

/** 创建字典条目 → 返回 id */
export function createDict(params: DictSaveParams): Promise<number> {
  return http.post<number>('/v1/dicts', params).then((res) => res.data)
}

/** 更新字典条目 */
export function updateDict(params: DictSaveParams): Promise<void> {
  return http.put<void>('/v1/dicts', params).then(() => undefined)
}

/** 删除字典条目 */
export function deleteDict(id: number | string): Promise<void> {
  return http.delete<void>(`/v1/dicts/${id}`).then(() => undefined)
}
