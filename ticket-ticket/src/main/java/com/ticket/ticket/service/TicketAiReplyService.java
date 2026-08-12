package com.ticket.ticket.service;

import com.ticket.ai.client.TicketReplier;
import com.ticket.ai.client.dto.ChatMessage;
import com.ticket.ai.client.dto.ReplyContext;
import com.ticket.ai.client.dto.ReplyResult;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.service.AIRecordPersistService;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.enums.CommentType;
import com.ticket.ticket.vo.TicketAiReplyVO;
import com.ticket.ticket.vo.TicketCommentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 智能回复 Service（ticket 08）。
 * <p>
 * 职责：接收工单 id，构建上下文（基本信息 + 多轮对话历史），调用 AI 回复接口。
 * <p>
 * <b>设计说明</b>：
 * <ul>
 *     <li>入参只有 ticketId —— 调用方（Controller）不需要知道内部组装逻辑</li>
 *     <li>INTERNAL 评论不参与 AI 对话上下文（ADR-0034 内部备注语义）</li>
 *     <li>REPLY 场景的 ai_ticket_record 落库由 DeepSeekReplier（Layer 1）处理，
 *         caller 不重复写（符合 spec 双层防线设计）</li>
 * </ul>
 *
 * @see TicketReplier
 * @see ReplyContext
 */
@Service
public class TicketAiReplyService {

    private static final Logger log = LoggerFactory.getLogger(TicketAiReplyService.class);

    private final TicketInfoService ticketInfoService;
    private final TicketCommentService ticketCommentService;
    private final TicketReplier ticketReplier;
    private final AIRecordPersistService aiRecordPersistService;

    public TicketAiReplyService(TicketInfoService ticketInfoService,
                               TicketCommentService ticketCommentService,
                               TicketReplier ticketReplier,
                               AIRecordPersistService aiRecordPersistService) {
        this.ticketInfoService = ticketInfoService;
        this.ticketCommentService = ticketCommentService;
        this.ticketReplier = ticketReplier;
        this.aiRecordPersistService = aiRecordPersistService;
    }

    /**
     * 获取工单 AI 智能回复。
     *
     * @param ticketId 工单 id
     * @return AI 回复结果 VO
     * @throws BusinessException {@code TICKET_NOT_FOUND} 当工单不存在
     */
    public TicketAiReplyVO getAiReply(Long ticketId) {
        // 1. 加载工单实体
        TicketInfo ticket = ticketInfoService.loadEntity(ticketId);
        if (ticket == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND,
                    "工单不存在: id=" + ticketId);
        }

        // 2. 获取历史评论列表
        List<TicketCommentVO> comments = ticketCommentService.list(ticketId);

        // 3. 组装多轮对话历史（INTERNAL 跳过），用业务中性的 ChatMessage
        List<ChatMessage> historyMessages = buildHistoryMessages(comments);

        // 4. 构造 AI 回复上下文
        ReplyContext ctx = new ReplyContext(
                ticket.getTicketNo(),
                ticket.getTitle(),
                ticket.getContent(),
                ticket.getStatus().name(),
                historyMessages
        );

        // 5. 调用 AI 回复（REPLY 场景的 ai_ticket_record 落库由 DeepSeekReplier 处理）
        ReplyResult result = ticketReplier.reply(ctx);

        // 6. 落 AI 记录（成功路径；失败路径由 DeepSeekReplier 写了，这里只处理成功情况）
        Long recordId = null;
        if (!result.fallback()) {
            try {
                recordId = aiRecordPersistService.saveSuccess(
                        ticketId,
                        AiCallType.REPLY,
                        "deepseek-chat",
                        "",
                        result.reply()
                );
            } catch (RuntimeException e) {
                log.warn("AI 回复记录落库失败: ticketId={}, cause={}", ticketId, e.toString());
            }
        }

        // 7. 构造返回 VO（ReplyResult 已经准确告知是否降级，无需字符串对比）
        TicketAiReplyVO vo = new TicketAiReplyVO();
        vo.setReply(result.reply());
        vo.setRecordId(recordId != null && recordId > 0 ? recordId : null);
        vo.setFallback(result.fallback());
        return vo;
    }

    /**
     * 从评论列表组装业务中性的 ChatMessage 列表。
     * <p>
     * 规则（ADR-0034）：
     * <ul>
     *     <li>CUSTOMER → ChatMessage.user()</li>
     *     <li>AGENT → ChatMessage.assistant()</li>
     *     <li>INTERNAL → 跳过（内部备注不参与 AI 对话上下文）</li>
     * </ul>
     *
     * @param comments 工单评论列表（已按 createTime ASC 排序）
     * @return 消息历史列表
     */
    private List<ChatMessage> buildHistoryMessages(List<TicketCommentVO> comments) {
        List<ChatMessage> messages = new ArrayList<>();
        for (TicketCommentVO comment : comments) {
            CommentType type = comment.getCommentType();
            if (type == CommentType.INTERNAL) {
                continue;
            }
            String content = comment.getContent();
            if (type == CommentType.CUSTOMER) {
                messages.add(ChatMessage.user(content));
            } else if (type == CommentType.AGENT) {
                messages.add(ChatMessage.assistant(content));
            }
        }
        return messages;
    }
}
