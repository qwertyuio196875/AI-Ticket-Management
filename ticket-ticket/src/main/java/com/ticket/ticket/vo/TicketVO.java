package com.ticket.ticket.vo;

import com.ticket.ticket.enums.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单展示 VO（ticket 05 / ticket-web 共享）。
 * <p>
 * 由 {@code ticket_info} entity 直接映射。ticket 05 暂不做 {@code sys_user} JOIN
 * 拼装昵称 —— 创建人/处理人昵称留给 ticket 06 引入 user-service 后再补
 * （避免在 ticket 05 阶段就为"未来要 JOIN"搭出过深模型）。
 * <p>
 * <b>展示字段约定</b>：
 * <ul>
 *     <li>枚举直接返（{@link TicketStatus}），前端按 i18n 字典渲染</li>
 *     <li>删除状态：VO 不暴露 {@code isDeleted}（删除对前端不可见）</li>
 * </ul>
 */
@Data
@Schema(description = "工单展示 VO")
public class TicketVO {

    @Schema(description = "工单主键", example = "1")
    private Long id;

    @Schema(description = "工单编号", example = "TK2026081200000001")
    private String ticketNo;

    @Schema(description = "工单标题", example = "无法连接公司内网")
    private String title;

    @Schema(description = "工单内容")
    private String content;

    @Schema(description = "工单分类（ticket_category.name）", example = "网络问题")
    private String type;

    @Schema(description = "优先级 HIGH / MEDIUM / LOW", example = "MEDIUM")
    private String priority;

    @Schema(description = "状态 PENDING / PROCESSING / RESOLVED / CLOSED", example = "PENDING")
    private TicketStatus status;

    @Schema(description = "创建人 sys_user.id", example = "1")
    private Long creatorId;

    @Schema(description = "处理人 sys_user.id（可空）", example = "3")
    private Long handlerId;

    @Schema(description = "创建时间", example = "2026-08-12T09:00:00")
    private LocalDateTime createTime;

    @Schema(description = "更新时间", example = "2026-08-12T09:00:00")
    private LocalDateTime updateTime;
}
