package com.ticket.ticket.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单附件 {@code ticket_attachment}（ticket 12，ADR-0014）。
 * <p>
 * 字段对齐 ticket 12 AC：
 * <ul>
 *     <li>{@code ticket_id} —— 关联 {@code ticket_info.id}（无外键约束，依赖 Service 校验）</li>
 *     <li>{@code file_url} —— 存储 key（OSS 为 {@code ticket/{yyyyMMdd}/{uuid}.{ext}}；
 *         本地降级模式为含 localPath 前缀的文件路径）</li>
 *     <li>{@code file_name} —— 原始文件名（展示用，与存储 key 解耦）</li>
 *     <li>{@code size} —— 字节数</li>
 *     <li>{@code mime_type} —— 上传时的 Content-Type</li>
 *     <li>{@code uploader_id} —— 上传人 {@code sys_user.id}（删除时"上传者本人或管理员"判定依据）</li>
 *     <li>{@code upload_time} —— 上传时间（列表按此 ASC 排序）</li>
 *     <li>{@code is_deleted} —— 软删标记；{@link #NOT_DELETED} = 0（默认），
 *         列表查询自动过滤（MP {@code @TableLogic}）</li>
 * </ul>
 * <p>
 * <b>索引</b>：{@code ticket_id} —— 列表查询主路径
 * {@code WHERE ticket_id = ? AND is_deleted = 0 ORDER BY upload_time ASC}。
 */
@Data
@TableName("ticket_attachment")
public class TicketAttachment {

    /** 未软删 —— 所有查询的默认过滤值 */
    public static final Integer NOT_DELETED = 0;
    /** 已软删 —— DELETE 端点设置此值 */
    public static final Integer DELETED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 工单主键 {@code ticket_info.id} */
    private Long ticketId;

    /** 存储 key（OSS 或本地降级路径） */
    private String fileUrl;

    /** 原始文件名（展示用） */
    private String fileName;

    /** 文件大小（字节） */
    private Long size;

    /** MIME 类型 */
    private String mimeType;

    /** 上传人 {@code sys_user.id} */
    private Long uploaderId;

    /** 上传时间 —— Service 层显式置 {@code LocalDateTime.now()} 落库 */
    private LocalDateTime uploadTime;

    /**
     * 软删标记 —— MP {@code @TableLogic} 自动在 {@code selectById / selectList / updateById} 加
     * {@code is_deleted = 0} 过滤条件。
     */
    @TableLogic(value = "0", delval = "1")
    private Integer isDeleted;
}
