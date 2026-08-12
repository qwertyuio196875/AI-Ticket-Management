package com.ticket.web.ticket;

import com.ticket.common.result.Result;
import com.ticket.ticket.aspect.OperationLog;
import com.ticket.ticket.service.TicketAiReplyService;
import com.ticket.ticket.vo.TicketAiReplyVO;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单 AI 智能回复 Controller（ticket 08）。
 * <p>
 * 端点：POST /api/v1/tickets/{id}/ai-reply
 * <p>
 * 权限说明：
 * <ul>
 *     <li>{@code ai:invoke} —— AI 调用权限，ticket 14 会加到 sys_menu 表</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class AiReplyController {

    private final TicketAiReplyService ticketAiReplyService;

    public AiReplyController(TicketAiReplyService ticketAiReplyService) {
        this.ticketAiReplyService = ticketAiReplyService;
    }

    /**
     * AI 智能回复端点。
     * <p>
     * 根据工单 id 获取工单信息 + 历史评论，调用 AI 生成排查思路 / 解决方案。
     *
     * @param id 工单 id（path variable）
     * @return AI 回复结果 VO
     */
    @PostMapping("/{id}/ai-reply")
    @PreAuthorize("hasAuthority('ai:invoke')")
    @OperationLog(value = "AI 智能回复", type = "TICKET")
    public Result<TicketAiReplyVO> aiReply(@PathVariable Long id) {
        return Result.success(ticketAiReplyService.getAiReply(id));
    }
}
