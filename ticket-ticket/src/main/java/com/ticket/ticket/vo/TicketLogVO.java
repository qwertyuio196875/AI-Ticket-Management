package com.ticket.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单时间线事件展示 VO（ticket 14，ticket-web 共享）。
 * <p>
 * 由 {@link com.ticket.ticket.entity.TicketLog} entity 映射，另拼装
 * {@code operatorName}（来自 {@code sys_user.nickname}，Service 层批量查询）。
 * <p>
 * <b>展示约定</b>：
 * <ul>
 *     <li>{@code eventType} 直接返枚举名（如 {@code CREATED}），前端按字典渲染中文</li>
 *     <li>{@code operatorId} / {@code operatorName} 可空 —— 系统事件（如 AI_CALLED）无操作人</li>
 *     <li>{@code createTime} 序列化格式与 {@link TicketVO#getCreateTime()} 一致（全局无自定义
 *         Jackson 配置，走 ISO-8601），不额外加 {@code @JsonFormat}</li>
 * </ul>
 */
@Data
@Schema(description = "工单时间线事件对象")
public class TicketLogVO {

    @Schema(description = "事件主键", example = "1")
    private Long id;

    @Schema(description = "工单主键", example = "100")
    private Long ticketId;

    @Schema(description = "事件类型（CREATED / UPDATED / STATUS_CHANGED / ASSIGNED / COMMENTED / AI_CALLED）",
            example = "CREATED")
    private String eventType;

    @Schema(description = "操作人 sys_user.id（系统事件可空）", example = "1")
    private Long operatorId;

    @Schema(description = "操作人昵称（sys_user.nickname，可空）", example = "工单发起人")
    private String operatorName;

    @Schema(description = "事件内容（key=value 文本）", example = "title=无法连接内网, type=NETWORK, priority=MEDIUM")
    private String content;

    @Schema(description = "事件时间", example = "2026-08-12T09:00:00")
    private LocalDateTime createTime;
}
