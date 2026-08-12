package com.ticket.ai.service.impl;

import com.ticket.ai.entity.AiTicketRecord;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.mapper.AiTicketRecordMapper;
import com.ticket.ai.service.AIRecordPersistService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * {@link AIRecordPersistService} 实现（ticket 08）。
 * <p>
 * 单表 insert，由 MyBatis Plus {@code BaseMapper.insert} 完成。失败兜底：catch 任何异常，
 * 只记 warn 日志，<b>不</b>向调用方抛——AI 落库失败不应影响主业务流。
 * <p>
 * <b>字段长度截断</b>：
 * <ul>
 *     <li>{@code responseContent} —— MySQL VARCHAR(4000)，截断到 4000 字符</li>
 *     <li>{@code errorLog} —— MySQL VARCHAR(1000)，截断到 1000 字符</li>
 * </ul>
 * 长内容靠 truncate 而非 throw，避免 AI 响应过大导致 DB 报错。
 */
@Service
public class AIRecordPersistServiceImpl implements AIRecordPersistService {

    private static final Logger log = LoggerFactory.getLogger(AIRecordPersistServiceImpl.class);

    /** response_content 字段长度上限（与 schema.sql VARCHAR(4000) 对齐） */
    private static final int MAX_RESPONSE_CONTENT_LEN = 4000;
    /** error_log 字段长度上限（与 schema.sql VARCHAR(1000) 对齐） */
    private static final int MAX_ERROR_LOG_LEN = 1000;

    private final AiTicketRecordMapper aiTicketRecordMapper;

    public AIRecordPersistServiceImpl(AiTicketRecordMapper aiTicketRecordMapper) {
        this.aiTicketRecordMapper = aiTicketRecordMapper;
    }

    @Override
    public Long save(Long recordId,
                     Long ticketId,
                     AiCallType callType,
                     String model,
                     String promptVersion,
                     String responseContent,
                     String errorLog) {
        AiTicketRecord record = new AiTicketRecord();
        record.setId(recordId);
        record.setTicketId(ticketId);
        record.setCallType(callType == null ? null : callType.name());
        record.setModel(model);
        record.setPromptVersion(promptVersion);
        record.setResponseContent(truncate(responseContent, MAX_RESPONSE_CONTENT_LEN));
        record.setErrorLog(errorLog == null ? null : truncate(errorLog, MAX_ERROR_LOG_LEN));
        record.setSuccess(errorLog == null);
        record.setCreateTime(LocalDateTime.now());

        try {
            aiTicketRecordMapper.insert(record);
            return record.getId();
        } catch (RuntimeException ex) {
            // AI 落库失败不影响主业务；只记 warn 日志
            log.warn("AI 调用记录落库失败: ticketId={}, callType={}, cause={}",
                    ticketId, callType, ex.toString());
            return -1L;
        }
    }

    /** 简单字符串截断（避免 StringIndexOutOfBoundsException） */
    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}