package com.ticket.ticket.vo;

import com.ticket.ticket.enums.TicketStatus;
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
public class TicketVO {

    private Long id;
    private String ticketNo;
    private String title;
    private String content;
    private String type;
    private String priority;
    private TicketStatus status;

    private Long creatorId;
    private Long handlerId;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
