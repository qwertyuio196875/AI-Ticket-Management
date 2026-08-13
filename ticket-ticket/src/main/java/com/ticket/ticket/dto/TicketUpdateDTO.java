package com.ticket.ticket.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新工单请求参数（ticket 05 AC）。
 * <p>
 * 当前 ticket 05 仅支持修改 title / content（AC：{@code PUT /{id} — update title/content}）。
 * <p>
 * <b>id 不在 DTO 必填</b>：从 {@code @PathVariable} 注入，Controller 在方法体里
 * {@code dto.setId(id)} 手动设置。若 DTO 强制 @NotNull，{@code @Valid} 会在
 * 路径参数写入之前触发校验，把所有 PUT 端点打成 400。
 * <p>
 * <b>不在 DTO 里的字段</b>：status / handler_id / priority / type —— 留待 ticket 06 +
 * ticket 08 通过专门端点（{@code /change-status} / {@code /assign} / AI 分类回调）操作。
 * <p>
 * <b>权限校验</b>：Service 层校验"创建人或管理员"，DTO 自身不持有用户上下文。
 */
@Data
@Schema(description = "更新工单参数")
public class TicketUpdateDTO {

    /** 由 Controller 从路径参数注入；不参与 @Valid 校验 */
    private Long id;

    @Size(max = 100, message = "工单标题长度不能超过 100 字符")
    private String title;

    @Size(max = 2000, message = "工单内容长度不能超过 2000 字符")
    private String content;
}
