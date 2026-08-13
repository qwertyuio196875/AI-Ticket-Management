package com.ticket.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 工单分配请求参数（ticket 06 AC）。
 * <p>
 * 对应端点 {@code PUT /api/v1/tickets/{id}/assign}。
 * <p>
 * <b>校验规则</b>（由 Service 层执行）：
 * <ul>
 *     <li>{@code handlerId} 必填且对应 {@code sys_user.id} 存在且 {@code status = 1}，
 *         否则抛 {@code USER_NOT_FOUND}（{@code S0101}）</li>
 *     <li>赋值成功后同事务写 {@code ticket_log(event=ASSIGNED)}；如果工单当前
 *         处于 PENDING，会一并触发状态迁移 PENDING → PROCESSING 并追加
 *         {@code ticket_log(event=STATUS_CHANGED)}</li>
 * </ul>
 * <p>
 * {@code reason} 可空，写入 {@code ticket_log.content}。
 */
@Data
@Schema(description = "分配工单处理人参数")
public class TicketAssignDTO {

    /** 处理人 sys_user.id —— 必填 */
    @NotNull(message = "处理人 id 不能为空")
    private Long handlerId;

    /** 分配原因 —— 可空，写入 ticket_log.content */
    @Size(max = 255, message = "分配原因长度不能超过 255 字符")
    private String reason;
}
