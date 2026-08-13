package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.vo.TicketLogVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketLogServiceImpl} 单元测试（ticket 14 工单时间线）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code list}：按 ticketId 查询 + 排序（{@code create_time ASC, id ASC}，
 *         由 wrapper 链式 orderByAsc 控制，排序正确性由集成测试验证）、
 *         operatorName 批量拼装、operatorId 为空时 operatorName 为空、无日志返回空列表</li>
 *     <li>工单不存在 → 抛 {@code TICKET_NOT_FOUND}（T0101），不查日志</li>
 *     <li>{@code ticketId} 为空 → 抛 {@code PARAM_INVALID}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketLogServiceImplTest {

    @Mock TicketLogMapper ticketLogMapper;
    @Mock TicketInfoMapper ticketInfoMapper;
    @Mock SysUserMapper sysUserMapper;

    @InjectMocks TicketLogServiceImpl service;

    private static final Long TICKET_ID = 100L;

    private TicketInfo activeTicket() {
        TicketInfo ticket = new TicketInfo();
        ticket.setId(TICKET_ID);
        ticket.setTicketNo("TK2026081200000001");
        ticket.setTitle("无法连接公司内网");
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setCreatorId(1L);
        return ticket;
    }

    @Test
    @DisplayName("list 成功：按 ticketId 查询 + operatorName 批量拼装")
    void list_queries_by_ticket_and_attaches_operator_name() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(activeTicket());

        TicketLog created = newLog(1L, TicketEventType.CREATED, 1L, LocalDateTime.of(2026, 8, 12, 9, 0));
        TicketLog assigned = newLog(2L, TicketEventType.ASSIGNED, 2L, LocalDateTime.of(2026, 8, 12, 10, 0));
        when(ticketLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(created, assigned));

        SysUser u1 = new SysUser();
        u1.setId(1L);
        u1.setNickname("工单发起人");
        SysUser u2 = new SysUser();
        u2.setId(2L);
        u2.setNickname("坐席小王");
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(u1, u2));

        List<TicketLogVO> result = service.list(TICKET_ID);

        // 按 mock 返回顺序映射（真实排序由 wrapper 的 orderByAsc 保证）
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getTicketId()).isEqualTo(TICKET_ID);
        assertThat(result.get(0).getEventType()).isEqualTo("CREATED");
        assertThat(result.get(0).getOperatorId()).isEqualTo(1L);
        assertThat(result.get(0).getOperatorName()).isEqualTo("工单发起人");
        assertThat(result.get(1).getEventType()).isEqualTo("ASSIGNED");
        assertThat(result.get(1).getOperatorName()).isEqualTo("坐席小王");

        // mapper 以非空 wrapper 查询过一次 —— 排序 / 过滤由 wrapper 构造保证
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LambdaQueryWrapper<TicketLog>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(ticketLogMapper).selectList(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    @Test
    @DisplayName("list 系统事件 operatorId 为空：operatorName 为 null")
    void list_system_event_with_null_operator_keeps_name_null() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(activeTicket());

        TicketLog aiLog = newLog(3L, TicketEventType.AI_CALLED, null, LocalDateTime.of(2026, 8, 12, 9, 30));
        when(ticketLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(aiLog));
        // operatorId 全为 null → 不查 sys_user（实现里 loadNicknames 提前返回）

        List<TicketLogVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventType()).isEqualTo("AI_CALLED");
        assertThat(result.get(0).getOperatorId()).isNull();
        assertThat(result.get(0).getOperatorName()).isNull();
    }

    @Test
    @DisplayName("list 工单无事件：返回空列表，不查 sys_user")
    void list_no_logs_returns_empty_list() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(activeTicket());
        when(ticketLogMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TicketLogVO> result = service.list(TICKET_ID);

        assertThat(result).isEmpty();
        verify(sysUserMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list 工单不存在：抛 TICKET_NOT_FOUND(T0101)，不查日志")
    void list_ticket_not_found_throws_T0101() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.list(TICKET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
        verify(ticketLogMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list ticketId 为空：抛 PARAM_INVALID，不查任何表")
    void list_null_ticket_id_throws_PARAM_INVALID() {
        assertThatThrownBy(() -> service.list(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(ticketInfoMapper, never()).selectById(any());
        verify(ticketLogMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ---------- 辅助 ----------

    private static TicketLog newLog(Long id, TicketEventType eventType, Long operatorId, LocalDateTime createTime) {
        TicketLog log = new TicketLog();
        log.setId(id);
        log.setTicketId(TICKET_ID);
        log.setEventType(eventType);
        log.setOperatorId(operatorId);
        log.setContent("dummy-content");
        log.setCreateTime(createTime);
        return log;
    }
}
