package com.ticket.ticket.vo;

import com.ticket.ticket.enums.CommentType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 工单评论展示 VO（ticket 07，ticket-web 共享）。
 * <p>
 * 字段对齐 {@link com.ticket.ticket.entity.TicketComment} entity —— 增 {@code creatorName}
 * 字段以展示创建人昵称（拼装由 Service 层 JOIN {@code sys_user} 拿）。
 * <p>
 * <b>展示约定</b>：
 * <ul>
 *     <li>枚举直接返（{@link CommentType}），前端按 i18n 字典渲染</li>
 *     <li>删除状态：VO 不暴露 {@code isDeleted}</li>
 *     <li>{@code content} 已是 escape 后的安全字符串 —— 前端可放心用 v-html 或 text</li>
 * </ul>
 */
@Data
@Schema(description = "工单评论展示对象")
public class TicketCommentVO {

    private Long id;
    private Long ticketId;
    private String content;
    private CommentType commentType;
    private Long creatorId;
    /** 创建人昵称（来自 sys_user.nickname，Service 拼装） */
    private String creatorName;
    private Long parentId;
    private LocalDateTime createTime;
}
