package com.ticket.web.ticket;

import com.ticket.common.result.Result;
import com.ticket.ticket.service.TicketLogService;
import com.ticket.ticket.vo.TicketLogVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 工单时间线接口（ticket 14，前端工单详情页"时间线"验收）。
 * <ul>
 *     <li>{@code GET /api/v1/tickets/{id}/logs} —— 工单业务事件流水
 *         （需 {@code ticket:view}，与评论列表同一读权限）</li>
 * </ul>
 * <p>
 * <b>Controller 只做参数校验和转发</b>（AGENTS.md §四约束）：工单存在性 / 排序 /
 * 操作人昵称拼装全部在 {@link TicketLogService} 内闭环；本端点不落
 * {@code @OperationLog}（纯读接口，审计只覆盖写端点）。
 */
@RestController
@RequestMapping("/api/v1/tickets/{id}/logs")
@Tag(name = "ticket", description = "工单管理：CRUD / 状态机 / 评论 / 导出")
public class TicketLogController {

    private final TicketLogService ticketLogService;

    public TicketLogController(TicketLogService ticketLogService) {
        this.ticketLogService = ticketLogService;
    }

    /**
     * 工单时间线（ticket_log 事件流水）。
     * <p>
     * 排序：{@code create_time ASC, id ASC}；事件类型以枚举名返回
     * （{@code CREATED / UPDATED / STATUS_CHANGED / ASSIGNED / COMMENTED / AI_CALLED}），
     * 前端按字典渲染中文；工单不存在抛 {@code T0101}。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ticket:view')")
    @Operation(summary = "查询工单时间线（业务事件流水）")
    public Result<List<TicketLogVO>> list(@PathVariable("id") Long ticketId) {
        return Result.success(ticketLogService.list(ticketId));
    }
}
