package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ticket.ticket.enums.CommentType;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单多轮对话 {@code ticket_comment}（ticket 07，详见 ADR-0034）。
 * <p>
 * 字段对齐 ticket 07 AC：
 * <ul>
 *     <li>{@code ticket_id} —— 关联 {@code ticket_info.id}（无外键约束，依赖 Service 校验）</li>
 *     <li>{@code content} —— 持久化前已做 XSS HTML escape（Service 层把守）</li>
 *     <li>{@code comment_type} —— 三枚举之一（{@link CommentType}）</li>
 *     <li>{@code parent_id} —— 可空；非空时表示这条评论是另一条评论的回复，
 *         Service 校验"父评论存在 + 父评论属于同一工单"（{@code T0105}）</li>
 *     <li>{@code creator_id} —— 创建人（{@code sys_user.id}）</li>
 *     <li>{@code is_deleted} —— 软删标记；{@link #NOT_DELETED} = 0（默认），
 *         列表查询自动过滤（MP {@code @TableLogic}）</li>
 * </ul>
 * <p>
 * <b>索引</b>：
 * <ul>
 *     <li>{@code ticket_id} —— 列表查询主路径 {@code WHERE ticket_id = ? AND is_deleted = 0 ORDER BY create_time ASC}</li>
 *     <li>{@code creator_id} —— 审计 / 查询"我创建的评论"路径</li>
 *     <li>{@code parent_id} —— 嵌套回复树构建</li>
 * </ul>
 */
@Data
@TableName("ticket_comment")
public class TicketComment {

    /** 未软删 —— 所有查询的默认过滤值 */
    public static final Integer NOT_DELETED = 0;
    /** 已软删 —— DELETE 端点设置此值 */
    public static final Integer DELETED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单主键 {@code ticket_info.id} */
    private Long ticketId;

    /** 回复内容（已 HTML escape） */
    private String content;

    /** 评论类型（{@link CommentType}） */
    private CommentType commentType;

    /** 创建人 {@code sys_user.id} */
    private Long creatorId;

    /** 父评论 id，可空；非空时 Service 校验"父存在 + 同一工单" */
    private Long parentId;

    /** 创建时间 —— Service 层显式置 {@code LocalDateTime.now()} 落库 */
    private LocalDateTime createTime;

    /**
     * 软删标记 —— MP {@code @TableLogic} 自动在 {@code selectById / selectList / updateById} 加
     * {@code is_deleted = 0} 过滤条件。
     */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
