package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.ticket.enums.TicketEventType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单业务事件流水 {@code ticket_log}（ticket 05）。
 * <p>
 * 工单维度的完整生命周期：状态迁移、分配、内容修改、AI 调用、多轮评论。
 * 由 Service 在业务动作的同事务中追加（ADR-0012），
 * 缺一日志等于业务错误 —— 写操作与 {@code ticket_info} 走同一事务。
 * <p>
 * 当前 ticket 05 仅落地 {@code CREATED} 事件。后续 ticket 会补齐
 * {@code UPDATED / STATUS_CHANGED / ASSIGNED / COMMENTED / AI_CALLED}。
 * <p>
 * <b>{@code content} 字段</b>：JSON 或纯文本，记事件相关上下文（例：创建时记
 * "title=..., type=..., priority=..."；状态变更时记 "from=PENDING, to=PROCESSING, handlerId=..."）。
 * 用 {@code TEXT} 即可，结构不强约束（spec AC 允许 "JSON or text"）。
 */
@Data
@TableName("ticket_log")
public class TicketLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单主键 —— {@code ticket_info.id} */
    private Long ticketId;

    /** 事件类型，枚举名持久化 */
    private TicketEventType eventType;

    /** 操作人 {@code sys_user.id}；系统事件可空（当前 ticket 05 不会触发） */
    private Long operatorId;

    /** 事件内容（JSON 或文本） */
    private String content;

    /**
     * 创建时间 —— Service 层显式置 {@code LocalDateTime.now()} 落库，
     * 不依赖 DB CURRENT_TIMESTAMP（避免应用与 DB 时钟漂移；
     * 同时支持同事务内多行写有先后顺序的时间戳）。
     */
    private LocalDateTime createTime;
}
