package com.ticket.ai.client.dto;

import com.ticket.ai.enums.TicketClassifyType;

import java.util.Objects;

/**
 * AI 工单分类结果（ticket 08）。
 * <p>
 * 由 DeepSeek 返回的强约束 JSON 解析而来；类型严格枚举（{@link TicketClassifyType}），
 * 优先级枚举（{@code LOW / MEDIUM / HIGH / URGENT}，与现有 ticket_info.priority VARCHAR(20) 对齐）。
 * <p>
 * <b>为什么 priority 用 String 而不是 enum？</b>ticket_info.priority 是 VARCHAR(20)，
 * 业务字典值可能扩展（如增加 "URGENT" 后又新加 "BLOCKER"），枚举写死会绑死 schema 演进；
 * 用 String 由业务层（TicketClassifyType 等）做白名单校验更稳妥。这里字段名也直接复用 DB 列名。
 * <p>
 * <b>为什么是 record？</b>分类结果是 immutable 的"值对象"，没有行为；record 简化代码并自动生成 equals/hashCode/toString，
 * 对 mock / 单测友好。
 *
 * @param type       分类（NETWORK / HARDWARE / SOFTWARE / ACCOUNT / OTHER）
 * @param priority   优先级（LOW / MEDIUM / HIGH / URGENT）
 * @param department 处理部门（中文描述，如 "网络运维" / "信息安全" / "待人工分配"）
 */
public record TicketClassifyResult(
        TicketClassifyType type,
        String priority,
        String department) {

    /**
     * 失败兜底值（双层防线第一层）：AI 抛异常 / JSON 解析失败时返回。
     * <p>
     * type=OTHER / priority=MEDIUM / department=待人工分配——与现有 ticket_info 默认值风格一致，
     * 保证 AI 失败不阻塞工单创建。
     */
    public static TicketClassifyResult fallback() {
        return new TicketClassifyResult(
                TicketClassifyType.OTHER,
                "MEDIUM",
                "待人工分配");
    }

    /** 校验字段非空（实现层在拿到 AI 响应后做防御性检查） */
    public boolean isValid() {
        return type != null
                && Objects.nonNull(priority) && !priority.isBlank()
                && Objects.nonNull(department) && !department.isBlank();
    }
}