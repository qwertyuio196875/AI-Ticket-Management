package com.ticket.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ai.entity.AiTicketRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 调用记录 Mapper（ticket 08）。
 * <p>
 * MyBatis Plus {@code BaseMapper} 提供 {@code insert / selectById / selectList} 等通用方法，
 * 当前 ticket 08 仅用到 {@code insert}，单测和集成测试通过 {@code selectList} 校验落库。
 */
@Mapper
public interface AiTicketRecordMapper extends BaseMapper<AiTicketRecord> {
}