package com.ticket.ticket.dto;

import com.ticket.ticket.enums.TicketStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单状态变更请求参数（ticket 06 AC）。
 * <p>
 * 对应端点 {@code PATCH /api/v1/tickets/{id}/status}。
 * <p>
 * <b>合法性校验</b>：
 * <ul>
 *     <li>{@code targetStatus} 必填；状态迁移合法性由 Service 层
 *         {@code TicketStatus.canTransitTo} 集中校验（ADR-0005），
 *         非法迁移抛 {@code TICKET_INVALID_TRANSITION}（业务异常码 {@code T0102}）</li>
 *     <li>{@code reason} 可空，写入 {@code ticket_log.content} 便于审计</li>
 * </ul>
 */
@Data
@Schema(description = "工单状态变更参数")
public class TicketStatusChangeDTO {

    /** 目标状态 —— 必填 */
    @NotNull(message = "目标状态不能为空")
    private TicketStatus targetStatus;

    /** 变更原因 —— 可空，写入 ticket_log.content */
    @Size(max = 255, message = "变更原因长度不能超过 255 字符")
    private String reason;
}
