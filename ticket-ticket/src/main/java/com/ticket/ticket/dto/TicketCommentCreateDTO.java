package com.ticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增工单评论请求参数（ticket 07 AC）。
 * <p>
 * 字段：
 * <ul>
 *     <li>{@code content}：必填，1-2000 字符（HTML escape 前）</li>
 *     <li>{@code commentType}：必填，字符串 CUSTOMER / AGENT / INTERNAL；
 *         Service 用 {@link com.ticket.ticket.enums.CommentType#fromValue(String)} 解析，
 *         非法值统一抛 {@code PARAM_INVALID}（{@code C0400}）</li>
 *     <li>{@code parentId}：可选，回复时填父评论 id</li>
 * </ul>
 * <p>
 * <b>为什么 commentType 用 String 而非枚举</b>：
 * Jackson 反序列化阶段遇到未知枚举值（如 {@code "FOO"}）会抛
 * {@code InvalidFormatException}，被 Spring 包装为
 * {@code HttpMessageNotReadableException}（默认 500）。改用 String + Service 层
 * {@code fromValue} 白名单解析，非法值明确返回 {@code 400 + C0400}，
 * 与 ticket 07 AC "comment_type must be one of 3 enum values" 一致。
 * <p>
 * <b>不在 DTO 里的字段</b>：
 * <ul>
 *     <li>{@code ticketId} —— 从 path 取</li>
 *     <li>{@code creatorId} —— 从 SecurityContext 取当前登录用户</li>
 *     <li>{@code createTime} —— Service 内部置 {@code LocalDateTime.now()}</li>
 * </ul>
 */
@Data
public class TicketCommentCreateDTO {

    @NotBlank(message = "评论内容不能为空")
    @Size(max = 2000, message = "评论内容长度不能超过 2000 字符")
    private String content;

    @NotNull(message = "评论类型不能为空")
    private String commentType;

    /** 父评论 id；非空表示这是对另一条评论的回复 */
    private Long parentId;
}
