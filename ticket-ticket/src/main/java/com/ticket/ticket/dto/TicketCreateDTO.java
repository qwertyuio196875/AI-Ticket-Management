package com.ticket.ticket.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建工单请求参数（ticket 05 AC）。
 * <p>
 * 字段对应 {@code ticket_info} 中创建时可填充的列：
 * <ul>
 *     <li>{@code title}：必填，1-100 字符</li>
 *     <li>{@code content}：必填，1-2000 字符</li>
 *     <li>{@code type}：可选 —— ticket 05 不校验字典，留空也能创建。
 *         ticket 08 AI 分类会写入</li>
 *     <li>{@code priority}：可选，HIGH / MEDIUM / LOW，缺省时 Service 层兜底为 MEDIUM</li>
 * </ul>
 * <p>
 * <b>不在 DTO 里的字段</b>：
 * <ul>
 *     <li>{@code ticketNo} —— Service 内部生成</li>
 *     <li>{@code status} —— 默认 PENDING</li>
 *     <li>{@code creatorId} —— 从 SecurityContext 取当前登录用户</li>
 *     <li>{@code handlerId} —— 创建时无处理人，NULL</li>
 * </ul>
 */
@Data
public class TicketCreateDTO {

    /** 默认优先级 —— {@code sys_dict(dict_type='priority').dict_value = 'MEDIUM'} */
    public static final String DEFAULT_PRIORITY = "MEDIUM";

    @NotBlank(message = "工单标题不能为空")
    @Size(max = 100, message = "工单标题长度不能超过 100 字符")
    private String title;

    @NotBlank(message = "工单内容不能为空")
    @Size(max = 2000, message = "工单内容长度不能超过 2000 字符")
    private String content;

    /** 工单分类（来自 ticket_category.name），可空 */
    @Size(max = 50, message = "工单分类长度不能超过 50 字符")
    private String type;

    /** 优先级 HIGH / MEDIUM / LOW，缺省时取 {@link #DEFAULT_PRIORITY} */
    @Size(max = 20, message = "优先级长度不能超过 20 字符")
    private String priority;
}
