package com.ticket.web.ticket;

import com.ticket.common.result.Result;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.aspect.OperationLog;
import com.ticket.ticket.dto.TicketCommentCreateDTO;
import com.ticket.ticket.service.TicketCommentService;
import com.ticket.ticket.vo.TicketCommentVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单评论接口（ticket 07，详见 ADR-0034）。
 * <ul>
 *     <li>{@code POST   /api/v1/tickets/{id}/comments}                  —— 新增评论（需 {@code ticket:comment}）</li>
 *     <li>{@code GET    /api/v1/tickets/{id}/comments}                  —— 评论列表（需 {@code ticket:view}；
 *         读权限与写权限解耦；INTERNAL 可见性由 Service 层按
 *         {@code ticket:comment} 判定）</li>
 *     <li>{@code DELETE /api/v1/tickets/{id}/comments/{commentId}}      —— 软删评论（需 {@code ticket:comment}；
 *         Service 层判创建者或管理员）</li>
 * </ul>
 * <p>
 * 三个端点统一挂 {@link OperationLog}，由 {@code OperationLogAspect} 自动落审计。
 * <p>
 * <b>Controller 只做参数校验和转发</b>（AGENTS.md §四约束）：业务校验
 * （工单存在 / 状态 / 父评论 / XSS / 权限）全部在 {@link TicketCommentService} 内闭环。
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}/comments")
@Tag(name = "ticket", description = "工单管理：CRUD / 状态机 / 评论 / 导出")
public class TicketCommentController {

    private final TicketCommentService ticketCommentService;

    public TicketCommentController(TicketCommentService ticketCommentService) {
        this.ticketCommentService = ticketCommentService;
    }

    /**
     * 新增评论。
     * <p>
     * 校验：工单存在 + 未 CLOSED；parentId 指向同工单评论；content 必填 ≤ 2000 字符；
     * 入库前已 HTML escape。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ticket:comment')")
    @OperationLog(value = "新增工单评论", type = "TICKET")
    @Operation(summary = "新增工单评论")
    public Result<Long> add(@PathVariable("id") Long ticketId,
                            @Valid @RequestBody TicketCommentCreateDTO dto) {
        Long commentId = ticketCommentService.add(ticketId, dto,
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(commentId);
    }

    /**
     * 评论列表（按 create_time ASC）。
     * <p>
     * Service 层按当前用户是否拥有 {@code ticket:comment} 决定 INTERNAL 可见性
     * （admin / agent 都视为 internal-staff，可看全部；其他角色看不到 INTERNAL）。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ticket:view')")
    @Operation(summary = "查询工单评论列表")
    public Result<List<TicketCommentVO>> list(@PathVariable("id") Long ticketId) {
        return Result.success(ticketCommentService.list(ticketId));
    }

    /**
     * 软删评论。
     * <p>
     * 权限：Service 层判"创建者本人或管理员"；
     * 走 MP {@code @TableLogic} 软删路径。
     */
    @DeleteMapping("/{commentId}")
    @PreAuthorize("hasAuthority('ticket:comment')")
    @OperationLog(value = "删除工单评论", type = "TICKET")
    @Operation(summary = "软删工单评论")
    public Result<Void> delete(@PathVariable("id") Long ticketId,
                               @PathVariable("commentId") Long commentId) {
        ticketCommentService.delete(ticketId, commentId,
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(null);
    }
}
