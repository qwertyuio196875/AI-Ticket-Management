/**
 * 时间展示格式化工具：后端 LocalDateTime 序列化为 ISO-8601（如 2026-08-12T09:00:00），
 * 这里统一格式化为 YYYY-MM-DD HH:mm（本地时区），避免各页面重复实现。
 */

function pad(n: number): string {
  return n < 10 ? `0${n}` : String(n)
}

/** 格式化为 YYYY-MM-DD HH:mm；空值 / 非法值返回占位「-」 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '-'
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 格式化为 YYYY-MM-DD；空值返回占位「-」 */
export function formatDate(value?: string | null): string {
  const text = formatDateTime(value)
  return text === '-' ? '-' : text.slice(0, 10)
}

/** 文件大小可读化（B / KB / MB） */
export function formatFileSize(size: number): string {
  if (size == null || Number.isNaN(size)) return '-'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}
