package com.ticket.ticket.service;

import com.ticket.ticket.vo.TicketAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工单附件 Service（ticket 12，ADR-0014）。
 * <p>
 * <b>职责边界</b>：所有 {@code ticket_attachment} 写操作必须经本接口。Service 层负责：
 * <ul>
 *     <li>工单存在性 + 状态校验（CLOSED 不允许上传）</li>
 *     <li>调用 {@code OssService} 上传 / 删除对象（存储层与业务解耦，见 ADR-0014）</li>
 *     <li>附件元数据落库（{@code file_url} 存存储 key）与软删（{@code is_deleted = 1}）</li>
 *     <li>列表查询按 {@code upload_time ASC} 返回，逐项生成签名下载 URL</li>
 *     <li>删除权限：仅上传者本人或管理员（{@code hasAuthority("admin")}）</li>
 * </ul>
 * <p>
 * <b>删除顺序约定</b>：先删存储对象（失败抛 {@code A0202}，元数据保留可重试），
 * 后软删元数据 —— 避免"元数据已删但对象残留"的孤儿文件。
 */
public interface TicketAttachmentService {

    /**
     * 上传附件（ticket 12 AC）。
     * <p>
     * 流程：校验工单存在 + 未软删 + 状态非 CLOSED（不满足 → 抛 {@code T0101} / {@code T0103}）
     * → {@code OssService.upload(file)}（内部做大小 / mime 白名单校验，失败抛 {@code C0400} 或 {@code A0201}）
     * → 落 {@code ticket_attachment} 元数据 → 返回 VO（{@code downloadUrl} = 默认 1h 签名 URL）。
     *
     * @param ticketId   工单主键（来自 path）
     * @param file       上传文件（multipart）
     * @param operatorId 当前登录用户 id（上传人）
     * @return 附件 VO（含签名下载 URL）
     */
    TicketAttachmentVO upload(Long ticketId, MultipartFile file, Long operatorId);

    /**
     * 获取工单附件列表（ticket 12 AC）。
     * <p>
     * 排序：{@code ORDER BY upload_time ASC}；过滤：软删自动隐藏（MP {@code @TableLogic}）。
     * <p>
     * 每个附件逐项生成签名下载 URL；单个生成失败记 WARN 日志并将该项
     * {@code downloadUrl} 置 {@code null}，不影响整列表返回。
     *
     * @param ticketId 工单主键
     * @return 附件列表（VO）
     */
    List<TicketAttachmentVO> list(Long ticketId);

    /**
     * 删除附件（ticket 12 AC）。
     * <p>
     * 权限：仅上传者本人或管理员（{@code hasAuthority("admin")}）。
     * 顺序：先 {@code OssService.delete(fileUrl)} 物理删对象（失败抛 {@code A0202}，
     * 元数据保留可重试），后 {@code deleteById} 软删元数据。
     *
     * @param ticketId     工单主键
     * @param attachmentId 附件主键
     * @param operatorId   当前登录用户 id
     */
    void delete(Long ticketId, Long attachmentId, Long operatorId);
}
