package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.ticket.enums.TicketStatus;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单主表 {@code ticket_info}（ticket 05）。
 * <p>
 * 字段对齐 ticket 05 AC：
 * <ul>
 *     <li>{@code ticket_no}：工单编号，由 {@code TicketNoGenerator} 按
 *         {@code TK{yyyyMMdd}{9 位 sequence}} 生成（ADR-0006）</li>
 *     <li>{@code type} / {@code priority}：工单分类 / 优先级，对应
 *         {@code ticket_category.name} / {@code sys_dict(dict_type='priority')}</li>
 *     <li>{@code status}：状态枚举名（{@link TicketStatus}）直接持久化</li>
 *     <li>{@code handler_id}：可空（工单刚创建时无处理人）</li>
 *     <li>{@code is_deleted}：软删标记，列表查询一律过滤 0</li>
 * </ul>
 * <p>
 * <b>索引</b>（联合索引设计见 spec Phase 5，EXPLAIN 验证留给 ticket 09）：
 * <ul>
 *     <li>{@code status} 单列</li>
 *     <li>{@code handler_id} 单列</li>
 *     <li>{@code create_time} 单列</li>
 *     <li>{@code (status, handler_id, create_time)} 联合</li>
 * </ul>
 */
@Data
@TableName("ticket_info")
public class TicketInfo {

    /** 未软删 —— 所有查询的默认过滤值 */
    public static final Integer NOT_DELETED = 0;
    /** 已软删 —— DELETE 端点设置此值 */
    public static final Integer DELETED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单编号，全局唯一，{@code TK{yyyyMMdd}{9 位}} */
    private String ticketNo;

    /** 工单标题 */
    private String title;

    /** 工单内容（详细描述） */
    private String content;

    /**
     * 工单分类（来自 {@code ticket_category.name}，ticket 04 落地）。
     * <p>
     * ticket 05 暂不校验分类合法性，落到字段里即可；ticket 08 AI 分类会写入。
     */
    private String type;

    /**
     * 优先级（来自 {@code sys_dict(dict_type='priority').dict_value}）。
     * <p>
     * 取值：HIGH / MEDIUM / LOW —— 与 ticket 04 数据字典种子对齐。
     */
    private String priority;

    /** 状态 —— 枚举名持久化，默认 PENDING */
    private TicketStatus status;

    /** 创建人 {@code sys_user.id} */
    private Long creatorId;

    /** 处理人 {@code sys_user.id}，可空 */
    private Long handlerId;

    /** 创建时间 —— DB 默认 CURRENT_TIMESTAMP，Service 不需显式设 */
    private LocalDateTime createTime;

    /** 更新时间 —— DB 默认 CURRENT_TIMESTAMP，UPDATE 时由 DB 维护 */
    private LocalDateTime updateTime;

    /**
     * 软删标记 —— MP {@code @TableLogic} 自动在 {@code selectById / selectList / updateById} 加
     * {@code is_deleted = 0} 过滤条件。
     * <p>
     * 默认值在 Service 层显式置 {@link #NOT_DELETED}，避免依赖 DB 默认值（DDL 默认值兜底）。
     */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
