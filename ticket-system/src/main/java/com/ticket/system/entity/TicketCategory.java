package com.ticket.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单分类 {@code ticket_category}（ticket 04）。
 * <p>
 * 管理员可配置的工单分类字典；AI 分类结果（type）应与本表对齐。
 * name 唯一索引。
 */
@Data
@TableName("ticket_category")
public class TicketCategory {

    /** 启用 */
    public static final Integer STATUS_ENABLED = 1;

    /** 禁用 */
    public static final Integer STATUS_DISABLED = 0;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 分类名（中文展示） */
    private String name;

    /** 分类描述 */
    private String description;

    /** 排序号，升序 */
    private Integer sort;

    /** 状态：1 启用 / 0 禁用 */
    private Integer status;

    private LocalDateTime createTime;
}
