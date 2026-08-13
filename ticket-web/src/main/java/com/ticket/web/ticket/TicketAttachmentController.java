package com.ticket.web.ticket;

import com.ticket.common.result.Result;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.aspect.OperationLog;
import com.ticket.ticket.service.TicketAttachmentService;
import com.ticket.ticket.vo.TicketAttachmentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 工单附件接口（ticket 12，详见 ADR-0014）。
 * <ul>
 *     <li>{@code POST   /api/v1/tickets/{id}/attachments}                —— 上传附件（需 {@code ticket:upload}）</li>
 *     <li>{@code GET    /api/v1/tickets/{id}/attachments}                —— 附件列表（需 {@code ticket:view}；
 *         返回带签名下载 URL 的 VO）</li>
 *     <li>{@code DELETE /api/v1/tickets/{id}/attachments/{attachmentId}} —— 删除附件（需 {@code ticket:upload}；
 *         Service 层判上传者或管理员）</li>
 * </ul>
 * <p>
 * 两个写端点统一挂 {@link OperationLog}，由 {@code OperationLogAspect} 自动落审计。
 * <p>
 * <b>Controller 只做参数校验和转发</b>（AGENTS.md §四约束）：文件大小 / mime 白名单、
 * 工单存在 / CLOSED、删除权限等业务校验全部在
 * {@link TicketAttachmentService} / {@code OssService} 内闭环。
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}/attachments")
@Tag(name = "ticket", description = "工单管理：CRUD / 状态机 / 评论 / 导出 / 附件")
public class TicketAttachmentController {

    private final TicketAttachmentService ticketAttachmentService;

    public TicketAttachmentController(TicketAttachmentService ticketAttachmentService) {
        this.ticketAttachmentService = ticketAttachmentService;
    }

    /**
     * 上传附件。
     * <p>
     * 校验：工单存在 + 未 CLOSED；文件 ≤ 50MB + mime 白名单（Service / OssService 把关）；
     * 返回 VO 含 {@code downloadUrl}（默认 1h 签名 URL）。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ticket:upload')")
    @OperationLog(value = "上传工单附件", type = "TICKET")
    @Operation(summary = "上传工单附件")
    public Result<TicketAttachmentVO> upload(@PathVariable("id") Long ticketId,
                                             @RequestParam("file") MultipartFile file) {
        TicketAttachmentVO vo = ticketAttachmentService.upload(ticketId, file,
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(vo);
    }

    /**
     * 附件列表（按 upload_time ASC）。
     * <p>
     * 每个附件逐项生成签名下载 URL；单个生成失败该项为 {@code null}，不影响整列表。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ticket:view')")
    @Operation(summary = "查询工单附件列表")
    public Result<List<TicketAttachmentVO>> list(@PathVariable("id") Long ticketId) {
        return Result.success(ticketAttachmentService.list(ticketId));
    }

    /**
     * 删除附件。
     * <p>
     * 权限：Service 层判"上传者本人或管理员"；先物理删存储对象（失败保留元数据可重试），
     * 后软删元数据。
     */
    @DeleteMapping("/{attachmentId}")
    @PreAuthorize("hasAuthority('ticket:upload')")
    @OperationLog(value = "删除工单附件", type = "TICKET")
    @Operation(summary = "删除工单附件")
    public Result<Void> delete(@PathVariable("id") Long ticketId,
                               @PathVariable("attachmentId") Long attachmentId) {
        ticketAttachmentService.delete(ticketId, attachmentId,
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(null);
    }
}
