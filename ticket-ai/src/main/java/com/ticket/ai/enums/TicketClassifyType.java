package com.ticket.ai.enums;

/**
 * AI 工单分类枚举（ticket 08）。
 * <p>
 * 类型严格枚举：DeepSeek Prompt 强约束输出五种类型之一；模型不识别时由实现层 catch 后走兜底（OTHER）。
 * <p>
 * <b>命名规范</b>：与 ticket_category.name（中文名）解耦——AI 分类输出 enum key，
 * 业务层（如未来 ticket-ticket Service）按需要把 enum key 映射到 ticket_category.id。
 * 当前 ticket 08 只把 {@code type.name()} 写到 ticket_info.type 字段，不做映射。
 */
public enum TicketClassifyType {

    /** 网络类（VPN / Wi-Fi / DNS / 防火墙 等） */
    NETWORK,
    /** 硬件类（笔记本 / 打印机 / 显示器 等） */
    HARDWARE,
    /** 软件类（应用系统 / 办公软件 / 操作系统 等） */
    SOFTWARE,
    /** 账号类（账号申请 / 权限变更 / 密码重置 等） */
    ACCOUNT,
    /** 其他（兜底：AI 解析失败 / 类型不在枚举内） */
    OTHER
}