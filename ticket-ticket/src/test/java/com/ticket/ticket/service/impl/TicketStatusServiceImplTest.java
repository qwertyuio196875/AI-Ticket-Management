package com.ticket.ticket.service.impl;

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
import com.ticket.ticket.service.cache.TicketCacheService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketStatusServiceImpl} 单元测试（ticket 06 AC）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code changeStatus}：合法迁移写 ticket_log，非法抛 T0102，工单不存在抛 T0101</li>
 *     <li>{@code assign}：handler 校验（不存在/禁用抛 S0101）、handler 变化才写 ASSIGNED 日志、
 *         PENDING 时同时写 STATUS_CHANGED 日志、非 PENDING 时不触发状态迁移</li>
 *     <li>{@code close}：复用 changeStatus 的语义，写 STATUS_CHANGED 日志，reason 缺省时为 "closed"</li>
 *     <li>{@code ticket_info} 与 {@code ticket_log} 写入次数的对应关系</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketStatusServiceImplTest {

    @Mock TicketInfoMapper ticketInfoMapper;
    @Mock TicketLogMapper ticketLogMapper;
    @Mock SysUserMapper sysUserMapper;
    @Mock TicketCacheService ticketCacheService;

    @InjectMocks TicketStatusServiceImpl service;

    private static final Long TICKET_ID = 100L;
    private static final Long HANDLER_ID = 7L;
    private static final Long OPERATOR_ID = 1L;

    private TicketInfo baseTicket;

    @BeforeEach
    void setUp() {
        baseTicket = new TicketInfo();
        baseTicket.setId(TICKET_ID);
        baseTicket.setTicketNo("TK2026081000000001");
        baseTicket.setTitle("测试工单");
        baseTicket.setStatus(TicketStatus.PENDING);
        baseTicket.setCreatorId(OPERATOR_ID);
        baseTicket.setHandlerId(null);
        baseTicket.setIsDeleted(TicketInfo.NOT_DELETED);
    }

    // ==================== changeStatus ====================

    @Test
    @DisplayName("changeStatus 合法迁移：更新 status + 写 STATUS_CHANGED 日志")
    void changeStatus_legal_transition_updates_and_writes_log() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.changeStatus(TICKET_ID, TicketStatus.PROCESSING, "认领处理", OPERATOR_ID);

        // ticket_info 应当被更新成 PROCESSING
        assertThat(baseTicket.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        verify(ticketInfoMapper, times(1)).updateById(baseTicket);

        // ticket_log 应当写一条 STATUS_CHANGED
        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        TicketLog written = logCaptor.getValue();
        assertThat(written.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(written.getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(written.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(written.getContent()).contains("from=PENDING").contains("to=PROCESSING").contains("reason=认领处理");
        assertThat(written.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("changeStatus 非法迁移：抛 T0102，DB 不写")
    void changeStatus_illegal_transition_throws_T0102_no_db_write() {
        // 当前 PENDING，尝试迁移到 RESOLVED —— 非法（ADR-0005）
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        assertThatThrownBy(() -> service.changeStatus(TICKET_ID, TicketStatus.RESOLVED, "no", OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_INVALID_TRANSITION.getCode());

        verify(ticketInfoMapper, never()).updateById(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("changeStatus 工单不存在：抛 T0101")
    void changeStatus_ticket_not_found_throws_T0101() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.changeStatus(TICKET_ID, TicketStatus.PROCESSING, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("changeStatus 不传 reason：content 中不出现 reason 段")
    void changeStatus_without_reason_omits_reason_in_log_content() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.changeStatus(TICKET_ID, TicketStatus.PROCESSING, null, OPERATOR_ID);

        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent()).doesNotContain("reason=");
    }

    // ==================== assign ====================

    @Test
    @DisplayName("assign PENDING 工单：写 handler、触发状态迁移、写 ASSIGNED + STATUS_CHANGED 两条日志")
    void assign_pending_ticket_writes_two_logs_and_changes_status() {
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(enabledUser(HANDLER_ID));
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.assign(TICKET_ID, HANDLER_ID, "团队分配", OPERATOR_ID);

        // ticket_info 应当被 update 两次：一次写 handler_id，一次写 status
        ArgumentCaptor<TicketInfo> ticketCaptor = ArgumentCaptor.forClass(TicketInfo.class);
        verify(ticketInfoMapper, times(2)).updateById(ticketCaptor.capture());
        // 第二次 update 时 status 应当是 PROCESSING
        TicketInfo lastUpdate = ticketCaptor.getAllValues().get(1);
        assertThat(lastUpdate.getHandlerId()).isEqualTo(HANDLER_ID);
        assertThat(lastUpdate.getStatus()).isEqualTo(TicketStatus.PROCESSING);

        // ticket_log 应当写两条：ASSIGNED + STATUS_CHANGED
        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(2)).insert(logCaptor.capture());
        TicketLog first = logCaptor.getAllValues().get(0);
        TicketLog second = logCaptor.getAllValues().get(1);
        assertThat(first.getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(first.getContent()).contains("handlerId=" + HANDLER_ID).contains("reason=团队分配");
        assertThat(second.getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
        assertThat(second.getContent()).contains("from=PENDING").contains("to=PROCESSING").contains("reason=assign");
    }

    @Test
    @DisplayName("assign PROCESSING 工单：仅写 ASSIGNED 日志，不动状态")
    void assign_processing_ticket_writes_only_assigned_log() {
        baseTicket.setStatus(TicketStatus.PROCESSING);
        baseTicket.setHandlerId(99L); // 旧 handler
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(enabledUser(HANDLER_ID));
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.assign(TICKET_ID, HANDLER_ID, "换人", OPERATOR_ID);

        // 只 update 一次：只写 handler_id，状态保持 PROCESSING
        verify(ticketInfoMapper, times(1)).updateById(any());
        assertThat(baseTicket.getHandlerId()).isEqualTo(HANDLER_ID);
        assertThat(baseTicket.getStatus()).isEqualTo(TicketStatus.PROCESSING);

        // 只写 ASSIGNED 日志
        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        TicketLog written = logCaptor.getValue();
        assertThat(written.getEventType()).isEqualTo(TicketEventType.ASSIGNED);
        assertThat(written.getContent())
                .contains("handlerId=" + HANDLER_ID)
                .contains("previousHandlerId=99")
                .contains("reason=换人");
    }

    @Test
    @DisplayName("assign handler 不变 + PENDING：不写 ASSIGNED 日志，但触发 PENDING→PROCESSING 状态迁移")
    void assign_same_handler_on_pending_only_transitions_status() {
        baseTicket.setHandlerId(HANDLER_ID); // 当前 handler == 新 handler
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(enabledUser(HANDLER_ID));
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.assign(TICKET_ID, HANDLER_ID, null, OPERATOR_ID);

        // ticket_info 仍然 update 1 次（仅状态字段变化）
        verify(ticketInfoMapper, times(1)).updateById(any());
        assertThat(baseTicket.getStatus()).isEqualTo(TicketStatus.PROCESSING);

        // ticket_log 只写 STATUS_CHANGED（不写 ASSIGNED —— handler 未变）
        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getEventType()).isEqualTo(TicketEventType.STATUS_CHANGED);
    }

    @Test
    @DisplayName("assign handler 不变 + 非 PENDING：完全 no-op（无 SQL、无日志）")
    void assign_same_handler_on_non_pending_is_noop() {
        baseTicket.setStatus(TicketStatus.PROCESSING);
        baseTicket.setHandlerId(HANDLER_ID); // 当前 handler == 新 handler
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(enabledUser(HANDLER_ID));
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.assign(TICKET_ID, HANDLER_ID, null, OPERATOR_ID);

        // 完全 no-op —— 任何 DB 写都不发生
        verify(ticketInfoMapper, never()).updateById(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("assign handlerId 不存在：抛 USER_NOT_FOUND(S0101)")
    void assign_with_nonexistent_handler_throws_USER_NOT_FOUND() {
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.assign(TICKET_ID, HANDLER_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.USER_NOT_FOUND.getCode());
        verify(ticketInfoMapper, never()).selectById(any());
        verify(ticketInfoMapper, never()).updateById(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("assign handler 禁用：抛 USER_NOT_FOUND")
    void assign_with_disabled_handler_throws_USER_NOT_FOUND() {
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(disabledUser(HANDLER_ID));

        assertThatThrownBy(() -> service.assign(TICKET_ID, HANDLER_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.USER_NOT_FOUND.getCode());
        verify(ticketInfoMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("assign 工单不存在：抛 T0101")
    void assign_ticket_not_found_throws_T0101() {
        when(sysUserMapper.selectById(HANDLER_ID)).thenReturn(enabledUser(HANDLER_ID));
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.assign(TICKET_ID, HANDLER_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("assign 工单 id 为空：抛 PARAM_INVALID")
    void assign_with_null_ticket_id_throws_PARAM_INVALID() {
        assertThatThrownBy(() -> service.assign(null, HANDLER_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(sysUserMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("assign handlerId 为空：抛 PARAM_INVALID")
    void assign_with_null_handler_id_throws_PARAM_INVALID() {
        assertThatThrownBy(() -> service.assign(TICKET_ID, null, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(sysUserMapper, never()).selectById(any());
    }

    // ==================== close ====================

    @Test
    @DisplayName("close 不传 reason：reason 默认为 \"closed\"")
    void close_without_reason_defaults_to_closed() {
        baseTicket.setStatus(TicketStatus.PROCESSING);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.close(TICKET_ID, null, OPERATOR_ID);

        // 状态变 CLOSED
        assertThat(baseTicket.getStatus()).isEqualTo(TicketStatus.CLOSED);
        // 写一条 STATUS_CHANGED 日志，reason="closed"
        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent())
                .contains("from=PROCESSING").contains("to=CLOSED").contains("reason=closed");
    }

    @Test
    @DisplayName("close 传 reason：写入 ticket_log.content")
    void close_with_reason_passes_through() {
        baseTicket.setStatus(TicketStatus.RESOLVED);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        service.close(TICKET_ID, "客户已确认解决", OPERATOR_ID);

        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent()).contains("reason=客户已确认解决");
    }

    @Test
    @DisplayName("close CLOSED 工单：非法迁移抛 T0102（CLOSED 是终态）")
    void close_already_closed_throws_T0102() {
        baseTicket.setStatus(TicketStatus.CLOSED);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        assertThatThrownBy(() -> service.close(TICKET_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.TICKET_INVALID_TRANSITION.getCode());
        verify(ticketInfoMapper, never()).updateById(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    // ---------- 辅助 ----------

    private SysUser enabledUser(Long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(SysUser.STATUS_ENABLED);
        return u;
    }

    private SysUser disabledUser(Long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setStatus(SysUser.STATUS_DISABLED);
        return u;
    }
}
