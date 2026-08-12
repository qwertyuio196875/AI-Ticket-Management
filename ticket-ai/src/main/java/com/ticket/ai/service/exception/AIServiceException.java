package com.ticket.ai.service.exception;

import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;

/**
 * AI 服务异常（ticket 08 spec line 19）。
 * <p>
 * 当前实现下 AI 失败一律走兜底（fallback）不抛异常；本类作为 spec 要求的占位 +
 * 未来 provider failover 切换时的扩展位。{@code BusinessExceptionCode.AI_UNAVAILABLE("A0101")}
 * 是其默认业务码。
 * <p>
 * <b>不</b>作为当前 AI 调用失败的主路径——失败仍走 {@code TicketClassifier.classify()}
 * 兜底值（OTHER/MEDIUM/待人工分配）或 {@code TicketReplier.reply()} 模板回复。
 */
public class AIServiceException extends BusinessException {

    public AIServiceException(String message) {
        super(BusinessExceptionCode.AI_UNAVAILABLE.getCode(), message);
    }

    public AIServiceException(String message, Throwable cause) {
        super(BusinessExceptionCode.AI_UNAVAILABLE.getCode(), message + ": " + cause.getMessage());
    }
}
