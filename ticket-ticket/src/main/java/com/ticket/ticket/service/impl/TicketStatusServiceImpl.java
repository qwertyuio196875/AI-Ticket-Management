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
import com.ticket.ticket.service.TicketStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * {@link TicketStatusService} 实现（ticket 06）。
 * <p>
 * <b>事务边界</b>：每个公开方法都标了 {@code @Transactional}，{@code ticket_info}
 * 写操作与 {@code ticket_log} 写入在同一事务（ADR-0012）。
 * <p>
 * <b>跨模块访问</b>：本类直接注入 {@link SysUserMapper}（属于 {@code ticket-system}
 * 模块）以校验 handler 存在性。{@code ticket-ticket} 的依赖规则允许读
 * {@code sys_user} 数据（"通过 mapper SQL JOIN 拿到，不在 Service 层跨模块调用"——
 * 见 {@code ticket-ticket/pom.xml} 注释）。这里走 mapper 而非
 * {@code SysUserService}，避免业务模块间横向依赖。
 * <p>
 * <b>不做的</b>（YAGNI）：
 * <ul>
 *     <li>Redis 缓存失效 —— ticket 09 引入，本类不做</li>
 *     <li>AI 通知处理人 —— ticket 08 / ticket 14 引入</li>
 *     <li>软删拦截 —— 调用方已用 {@link #loadActive} 过滤；状态机迁移不在
 *         "被软删后还能改"的语义里</li>
 * </ul>
 */
@Service
public class TicketStatusServiceImpl implements TicketStatusService {

    private static final Logger log = LoggerFactory.getLogger(TicketStatusServiceImpl.class);

    /** 关闭工单时缺省写入 ticket_log 的 reason —— 与 ticket 06 AC 对齐 */
    private static final String DEFAULT_CLOSE_REASON = "closed";

    private final TicketInfoMapper ticketInfoMapper;
    private final TicketLogMapper ticketLogMapper;
    private final SysUserMapper sysUserMapper;

    public TicketStatusServiceImpl(TicketInfoMapper ticketInfoMapper,
                                   TicketLogMapper ticketLogMapper,
                                   SysUserMapper sysUserMapper) {
        this.ticketInfoMapper = ticketInfoMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.sysUserMapper = sysUserMapper;
    }

    // ---------- changeStatus（唯一状态变更入口）----------

    @Override
    @Transactional
    public void changeStatus(Long ticketId, TicketStatus targetStatus, String reason, Long operatorId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        if (targetStatus == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "目标状态不能为空");
        }
        TicketInfo ticket = loadActive(ticketId);
        TicketStatus from = ticket.getStatus();
        // canTransitTo 集中校验（ADR-0005）—— 非法直接抛 T0102
        from.requireTransitTo(targetStatus);

        ticket.setStatus(targetStatus);
        ticketInfoMapper.updateById(ticket);

        appendLog(ticket.getId(), TicketEventType.STATUS_CHANGED, operatorId,
                buildStatusChangeContent(from, targetStatus, reason));

        this.log.info("工单状态变更: ticketId={}, from={}, to={}, operatorId={}",
                ticket.getId(), from, targetStatus, operatorId);
    }

    // ---------- assign ----------

    @Override
    @Transactional
    public void assign(Long ticketId, Long handlerId, String reason, Long operatorId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        if (handlerId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "处理人 id 不能为空");
        }

        // 1. handler 存在性 + 启用校验 —— 不存在 / 禁用统一抛 USER_NOT_FOUND（S0101）
        SysUser handler = sysUserMapper.selectById(handlerId);
        if (handler == null || !Objects.equals(SysUser.STATUS_ENABLED, handler.getStatus())) {
            throw BusinessException.of(BusinessExceptionCode.USER_NOT_FOUND,
                    "处理人不存在或已禁用: handlerId=" + handlerId);
        }

        // 2. 加载工单
        TicketInfo ticket = loadActive(ticketId);

        Long previousHandlerId = ticket.getHandlerId();
        boolean handlerChanged = !Objects.equals(previousHandlerId, handlerId);
        boolean needStatusTransition = ticket.getStatus() == TicketStatus.PENDING;

        // 3. 早返回：handler 未变 且 不需要状态迁移 —— 整笔调用 no-op，避免冗余 SQL
        if (!handlerChanged && !needStatusTransition) {
            this.log.info("分配 no-op: ticketId={}, handlerId 未变, 当前 status={}", ticketId, handlerId, ticket.getStatus());
            return;
        }

        // 4. 更新 handler_id（仅当 handler 变化时才写）
        if (handlerChanged) {
            ticket.setHandlerId(handlerId);
            ticketInfoMapper.updateById(ticket);
            appendLog(ticket.getId(), TicketEventType.ASSIGNED, operatorId,
                    buildAssignedContent(handlerId, previousHandlerId, reason));
        }

        // 5. 若当前状态为 PENDING，触发 PENDING → PROCESSING 状态迁移
        if (needStatusTransition) {
            // 复用 changeStatus 的语义会绕开本类内的 transaction proxy；这里直接走
            // 同方法内的私有逻辑，保证 ticket_info + ticket_log 同事务
            ticket.setStatus(TicketStatus.PROCESSING);
            ticketInfoMapper.updateById(ticket);
            appendLog(ticket.getId(), TicketEventType.STATUS_CHANGED, operatorId,
                    buildStatusChangeContent(TicketStatus.PENDING, TicketStatus.PROCESSING, "assign"));
        }

        this.log.info("工单分配: ticketId={}, handlerId={}, operatorId={}, handlerChanged={}, status={}",
                ticket.getId(), handlerId, operatorId, handlerChanged, ticket.getStatus());
    }

    // ---------- close ----------

    @Override
    @Transactional
    public void close(Long ticketId, String reason, Long operatorId) {
        // 复用 changeStatus 集中校验 —— 同事务自然延续，无需手动处理 proxy 边界
        changeStatus(ticketId, TicketStatus.CLOSED,
                StringUtils.hasText(reason) ? reason : DEFAULT_CLOSE_REASON,
                operatorId);
    }

    // ---------- 内部辅助 ----------

    /**
     * 加载工单（要求未软删）—— 与 {@code TicketInfoServiceImpl.loadEntity} 同语义。
     * <p>
     * 不复用 {@code TicketInfoService.loadEntity} 是为了避免 Service 间横向依赖：
     * {@code TicketStatusService} 与 {@code TicketInfoService} 同处 ticket-ticket 模块，
     * 但本类作为状态机入口不应被上层的可变性 API 牵动，故直接调 mapper。
     */
    private TicketInfo loadActive(Long id) {
        TicketInfo entity = ticketInfoMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }
        return entity;
    }

    /**
     * 追加 ticket_log —— 同事务内复用。
     * <p>
     * 与 {@code TicketInfoServiceImpl} 中 {@code ticketLogMapper.insert(...)}
     * 的写入风格保持一致：显式置 {@code createTime}（不依赖 DB CURRENT_TIMESTAMP，
     * 保证同事务内多行写有先后顺序的时间戳）。
     */
    private void appendLog(Long ticketId, TicketEventType eventType, Long operatorId, String content) {
        TicketLog log = new TicketLog();
        log.setTicketId(ticketId);
        log.setEventType(eventType);
        log.setOperatorId(operatorId);
        log.setContent(content);
        log.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(log);
    }

    /** STATUS_CHANGED 事件的 content 文本 */
    private String buildStatusChangeContent(TicketStatus from, TicketStatus to, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("from=").append(from);
        sb.append(", to=").append(to);
        if (StringUtils.hasText(reason)) {
            sb.append(", reason=").append(reason);
        }
        return sb.toString();
    }

    /** ASSIGNED 事件的 content 文本 */
    private String buildAssignedContent(Long newHandlerId, Long previousHandlerId, String reason) {
        StringBuilder sb = new StringBuilder();
        sb.append("handlerId=").append(newHandlerId);
        if (previousHandlerId != null) {
            sb.append(", previousHandlerId=").append(previousHandlerId);
        }
        if (StringUtils.hasText(reason)) {
            sb.append(", reason=").append(reason);
        }
        return sb.toString();
    }
}
