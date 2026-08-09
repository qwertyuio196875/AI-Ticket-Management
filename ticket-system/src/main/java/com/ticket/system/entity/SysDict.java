package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 数据字典 {@code sys_dict}（ticket 04）。
 * <p>
 * 将 priority / comment_type / status 等枚举的可选项抽到表里管理，
 * 前端下拉、后台管理页面均走这张表。
 * <p>
 * 唯一索引：(dict_type, dict_value)，同 type 下 value 不能重复。
 */
@Data
@TableName("sys_dict")
public class SysDict {

    /** 启用 */
    public static final Integer STATUS_ENABLED = 1;

    /** 禁用 */
    public static final Integer STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 字典类型，如 priority / comment_type / status */
    private String dictType;

    /** 字典值（代码里硬编码引用），如 HIGH / CUSTOMER */
    private String dictValue;

    /** 字典展示名（中文），如 高 / 客户 */
    private String dictLabel;

    /** 同 type 下的排序号，升序 */
    private Integer sort;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    /** 备注 */
    private String remark;

    private LocalDateTime createTime;
}
