package com.ticket.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新工单分类的请求参数（ticket 04）。
 * <p>
 * 字段语义：
 * <ul>
 *     <li>{@link #id}：更新时必传；创建时不传</li>
 *     <li>{@link #name}：创建 / 更新均需 {@code @NotBlank}，但更新时
 *         service 层 {@code toEntity} 按非空合并，name 通常保持不变</li>
 *     <li>其他字段（description / sort / status）创建 / 更新均生效，
 *         更新时按非空合并</li>
 * </ul>
 */
@Data
public class TicketCategorySaveDTO {

    /** 更新时必传；创建时可不传 */
    private Long id;

    @NotBlank(message = "分类名不能为空")
    @Size(max = 50, message = "分类名长度不能超过 50")
    private String name;

    private String description;

    private Integer sort;

    private Integer status;
}
