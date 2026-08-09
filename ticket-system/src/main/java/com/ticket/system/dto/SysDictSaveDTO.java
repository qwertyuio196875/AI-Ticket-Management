package com.ticket.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建 / 更新数据字典的请求参数（ticket 04）。
 * <p>
 * 字段语义：
 * <ul>
 *     <li>{@link #id}：更新时必传；创建时不传</li>
 *     <li>{@link #dictType} / {@link #dictValue}：仅在 <b>创建</b>路径生效。
 *         更新路径由 {@code (dict_type, dict_value)} 唯一索引决定业务键，
 *         业务上不允许改这两个字段；service 层 {@code toEntity} 会忽略这两个字段的更新值。
 *         创建时仍按 {@code @NotBlank} 校验必填</li>
 *     <li>其他字段（dictLabel / sort / status / remark）创建 / 更新均生效</li>
 * </ul>
 */
@Data
public class SysDictSaveDTO {

    /** 更新时必传；创建时可不传 */
    private Long id;

    @NotBlank(message = "字典类型不能为空")
    @Size(max = 50, message = "字典类型长度不能超过 50")
    private String dictType;

    @NotBlank(message = "字典值不能为空")
    @Size(max = 50, message = "字典值长度不能超过 50")
    private String dictValue;

    @NotBlank(message = "字典展示名不能为空")
    @Size(max = 50, message = "字典展示名长度不能超过 50")
    private String dictLabel;

    private Integer sort;

    private Integer status;

    private String remark;
}
