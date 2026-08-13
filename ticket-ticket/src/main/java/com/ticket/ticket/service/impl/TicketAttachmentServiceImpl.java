package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.entity.TicketAttachment;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.mapper.TicketAttachmentMapper;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.oss.OssService;
import com.ticket.ticket.service.TicketAttachmentService;
import com.ticket.ticket.vo.TicketAttachmentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link TicketAttachmentService} 实现（ticket 12，ADR-0014）。
 * <p>
 * <b>存储与元数据解耦</b>：底层走 {@link OssService}（真实 OSS 或本地降级），
 * 本类不感知存储实现；{@code file_url} 统一存存储 key。
 * <p>
 * <b>删除顺序</b>：{@link #delete} 先 {@code ossService.delete(fileUrl)}
 * （失败抛 {@code A0202}，元数据保留可重试），后 {@code deleteById} 软删元数据 ——
 * 避免"元数据已删但对象残留"的孤儿文件。
 * <p>
 * <b>删除权限</b>：{@code hasAuthority("admin")} 字符串判定沿用 ticket 05/06/07
 * "真正超级管理员"约定；{@code @PreAuthorize("ticket:upload")} 只保证请求方有上传权，
 * "能否删这条附件"由本类把关。
 */
@Service
public class TicketAttachmentServiceImpl implements TicketAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(TicketAttachmentServiceImpl.class);

    /** ticket 06 沿用的"超级管理员"权限字符串 */
    private static final String ADMIN_AUTHORITY = "admin";

    private final TicketInfoMapper ticketInfoMapper;
    private final TicketAttachmentMapper attachmentMapper;
    private final OssService ossService;

    public TicketAttachmentServiceImpl(TicketInfoMapper ticketInfoMapper,
                                       TicketAttachmentMapper attachmentMapper,
                                       OssService ossService) {
        this.ticketInfoMapper = ticketInfoMapper;
        this.attachmentMapper = attachmentMapper;
        this.ossService = ossService;
    }

    // ---------- upload ----------

    @Override
    public TicketAttachmentVO upload(Long ticketId, MultipartFile file, Long operatorId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        if (file == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "文件不能为空");
        }
        // 1. 工单存在性 + 未软删（ticket 12 AC 只要求存在校验，不做 CLOSED 拒绝 ——
        //    附件删除 / 列表不涉及状态机，状态约束不在本 ticket 验收范围内）
        TicketInfo ticket = ticketInfoMapper.selectById(ticketId);
        if (ticket == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }

        // 2. 上传存储对象（OssFileValidator 校验在 OssService 内部完成）
        //    OSS 失败抛 A0201 向上抛 —— 此时元数据不落库
        String fileKey = ossService.upload(file);

        // 3. 落元数据
        TicketAttachment attachment = new TicketAttachment();
        attachment.setTicketId(ticketId);
        attachment.setFileUrl(fileKey);
        attachment.setFileName(file.getOriginalFilename());
        attachment.setSize(file.getSize());
        attachment.setMimeType(file.getContentType());
        attachment.setUploaderId(operatorId);
        attachment.setUploadTime(LocalDateTime.now());
        attachment.setIsDeleted(TicketAttachment.NOT_DELETED);
        attachmentMapper.insert(attachment);

        log.info("上传工单附件: ticketId={}, attachmentId={}, fileName={}, fileUrl={}, operatorId={}",
                ticketId, attachment.getId(), attachment.getFileName(), fileKey, operatorId);
        // 4. 返回 VO，downloadUrl = 默认 1h 签名 URL
        return toVO(attachment, ossService.getSignedUrl(fileKey));
    }

    // ---------- list ----------

    @Override
    public List<TicketAttachmentVO> list(Long ticketId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        // 软删过滤由 @TableLogic 自动加 is_deleted = 0
        LambdaQueryWrapper<TicketAttachment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketAttachment::getTicketId, ticketId)
                .orderByAsc(TicketAttachment::getUploadTime);
        List<TicketAttachment> rows = attachmentMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        return rows.stream().map(a -> {
            String url;
            try {
                url = ossService.getSignedUrl(a.getFileUrl());
            } catch (RuntimeException e) {
                // 单个签名失败不影响整列表：WARN + downloadUrl 置 null
                log.warn("生成附件下载 URL 失败: attachmentId={}, fileUrl={}", a.getId(), a.getFileUrl(), e);
                url = null;
            }
            return toVO(a, url);
        }).collect(Collectors.toList());
    }

    // ---------- delete ----------

    @Override
    public void delete(Long ticketId, Long attachmentId, Long operatorId) {
        if (ticketId == null || attachmentId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id / 附件 id 不能为空");
        }
        TicketAttachment existing = attachmentMapper.selectById(attachmentId);
        if (existing == null || !Objects.equals(existing.getTicketId(), ticketId)) {
            throw BusinessException.of(BusinessExceptionCode.ATTACHMENT_NOT_FOUND);
        }
        // 权限：仅上传者本人或管理员
        boolean isUploader = existing.getUploaderId() != null
                && existing.getUploaderId().equals(operatorId);
        boolean isAdmin = SecurityContextUtils.hasAuthority(ADMIN_AUTHORITY);
        if (!isUploader && !isAdmin) {
            throw BusinessException.of(BusinessExceptionCode.AUTH_FORBIDDEN,
                    "仅上传者本人或管理员可以删除该附件");
        }
        // 先物理删对象（失败抛 A0202，元数据保留可重试），后软删元数据
        ossService.delete(existing.getFileUrl());
        int affected = attachmentMapper.deleteById(attachmentId);
        if (affected == 0) {
            throw BusinessException.of(BusinessExceptionCode.ATTACHMENT_NOT_FOUND,
                    "附件已被其他操作修改，请刷新后重试");
        }
        log.info("软删工单附件: ticketId={}, attachmentId={}, operatorId={}", ticketId, attachmentId, operatorId);
    }

    // ---------- 内部辅助 ----------

    private TicketAttachmentVO toVO(TicketAttachment entity, String downloadUrl) {
        TicketAttachmentVO vo = new TicketAttachmentVO();
        vo.setId(entity.getId());
        vo.setTicketId(entity.getTicketId());
        vo.setFileName(entity.getFileName());
        vo.setSize(entity.getSize());
        vo.setMimeType(entity.getMimeType());
        vo.setUploaderId(entity.getUploaderId());
        vo.setUploadTime(entity.getUploadTime());
        vo.setDownloadUrl(downloadUrl);
        return vo;
    }
}
