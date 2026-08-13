package com.ticket.ticket.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单附件展示 VO（ticket 12，ticket-web 共享）。
 * <p>
 * 字段对齐 {@link com.ticket.ticket.entity.TicketAttachment} entity —— 增 {@code downloadUrl}
 * 字段（Service 层通过 {@code OssService.getSignedUrl} 生成，默认 1 小时有效）。
 * <p>
 * <b>展示约定</b>：
 * <ul>
 *     <li>{@code downloadUrl}：私有 bucket 场景为签名 URL；本地降级模式为本地文件路径；
 *         生成失败时为 {@code null}（列表不因单个失败整体报错）</li>
 *     <li>删除状态：VO 不暴露 {@code isDeleted}</li>
 *     <li>文件大小 {@code size} 单位字节，前端自行格式化</li>
 * </ul>
 */
@Data
@Schema(description = "工单附件展示对象")
public class TicketAttachmentVO {

    private Long id;
    private Long ticketId;
    /** 原始文件名（展示用） */
    private String fileName;
    /** 文件大小（字节） */
    private Long size;
    /** MIME 类型 */
    private String mimeType;
    /** 上传人 {@code sys_user.id} */
    private Long uploaderId;
    private LocalDateTime uploadTime;
    /** 签名下载 URL（默认 1 小时有效；本地降级模式为本地文件路径；生成失败为 null） */
    private String downloadUrl;
}
