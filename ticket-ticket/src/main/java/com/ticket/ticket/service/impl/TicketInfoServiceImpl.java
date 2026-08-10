package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.dto.TicketCreateDTO;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.dto.TicketUpdateDTO;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.service.TicketInfoService;
import com.ticket.ticket.service.TicketNoGenerator;
import com.ticket.ticket.vo.TicketVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * {@link TicketInfoService} 实现（ticket 05）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li><b>事务原子</b>：{@code create} 内 {@code ticket_info} + {@code ticket_log}
 *         走同一 {@code @Transactional}，符合 ADR-0012</li>
 *     <li><b>权限校验</b>：{@code update} 服务层判"创建人或管理员"
 *         （{@code ticket:manage} 权限字符串），与 Controller 层
 *         {@code @PreAuthorize} 形成两道闸门</li>
 *     <li><b>软删过滤</b>：依赖 MP {@code @TableLogic}，所有查询自动加
 *         {@code is_deleted = 0}</li>
 *     <li><b>默认兜底</b>：{@code priority} 为空时取 {@code MEDIUM}</li>
 * </ul>
 * <p>
 * <b>不做的</b>（YAGNI）：
 * <ul>
 *     <li>Redis 详情缓存 —— ticket 09 引入</li>
 *     <li>状态机迁移 —— ticket 06 引入（{@code TicketStatus.canTransitTo} 已就绪）</li>
 *     <li>AI 分类 —— ticket 08 引入</li>
 *     <li>创建人/处理人昵称 JOIN —— 当前 ticket 05 用 {@code ticket_info.creatorId}
 *         单字段；VO 拼装昵称留到 ticket 06（届时 ticket 6+ 会引入 user service）</li>
 * </ul>
 */
@Service
public class TicketInfoServiceImpl implements TicketInfoService {

    /** 超级管理员角色 key —— 用于"创建人或管理员"判定 */
    private static final String ADMIN_ROLE_KEY = "admin";

    private static final Logger log = LoggerFactory.getLogger(TicketInfoServiceImpl.class);

    private final TicketInfoMapper ticketInfoMapper;
    private final TicketLogMapper ticketLogMapper;
    private final TicketNoGenerator ticketNoGenerator;

    public TicketInfoServiceImpl(TicketInfoMapper ticketInfoMapper,
                                 TicketLogMapper ticketLogMapper,
                                 TicketNoGenerator ticketNoGenerator) {
        this.ticketInfoMapper = ticketInfoMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.ticketNoGenerator = ticketNoGenerator;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(TicketCreateDTO dto) {
        Long creatorId = SecurityContextUtils.currentUserIdRequired();

        TicketInfo ticket = new TicketInfo();
        ticket.setTicketNo(ticketNoGenerator.next());
        ticket.setTitle(dto.getTitle());
        ticket.setContent(dto.getContent());
        ticket.setType(StringUtils.hasText(dto.getType()) ? dto.getType() : null);
        ticket.setPriority(StringUtils.hasText(dto.getPriority())
                ? dto.getPriority()
                : TicketCreateDTO.DEFAULT_PRIORITY);
        ticket.setStatus(TicketStatus.PENDING);
        ticket.setCreatorId(creatorId);
        ticket.setHandlerId(null);
        ticket.setIsDeleted(TicketInfo.NOT_DELETED);
        ticketInfoMapper.insert(ticket);

        // 同事务写 ticket_log(CREATED) —— ADR-0012
        TicketLog log = new TicketLog();
        log.setTicketId(ticket.getId());
        log.setEventType(TicketEventType.CREATED);
        log.setOperatorId(creatorId);
        log.setContent(buildCreatedLogContent(ticket));
        log.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(log);

        this.log.info("创建工单: ticketId={}, ticketNo={}, creatorId={}",
                ticket.getId(), ticket.getTicketNo(), creatorId);
        return ticket.getId();
    }

    // ---------- 分页查询 ----------

    @Override
    public IPage<TicketVO> page(TicketQueryDTO query) {
        long pageNum = query.getPageNum() == null || query.getPageNum() < 1 ? 1L : query.getPageNum();
        long pageSize = query.getPageSize() == null || query.getPageSize() < 1 ? 20L : query.getPageSize();

        Page<TicketInfo> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<TicketInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(query.getStatus() != null, TicketInfo::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getPriority()), TicketInfo::getPriority, query.getPriority())
                .eq(StringUtils.hasText(query.getType()), TicketInfo::getType, query.getType())
                .eq(query.getHandlerId() != null, TicketInfo::getHandlerId, query.getHandlerId())
                .ge(query.getDateFrom() != null, TicketInfo::getCreateTime,
                        query.getDateFrom() == null ? null : LocalDateTime.of(query.getDateFrom(), LocalTime.MIN))
                .le(query.getDateTo() != null, TicketInfo::getCreateTime,
                        query.getDateTo() == null ? null : LocalDateTime.of(query.getDateTo(), LocalTime.MAX))
                .orderByDesc(TicketInfo::getCreateTime);

        IPage<TicketInfo> entityPage = ticketInfoMapper.selectPage(page, wrapper);
        // IPage#convert 原地把 records 元素类型从 TicketInfo 变成 TicketVO，
        // 同时保留 total / pageNum / pageSize 等元信息
        return entityPage.convert(this::toVO);
    }

    // ---------- 详情 ----------

    @Override
    public TicketVO getById(Long id) {
        TicketInfo entity = loadEntity(id);
        return toVO(entity);
    }

    @Override
    public TicketInfo loadEntity(Long id) {
        if (id == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        TicketInfo entity = ticketInfoMapper.selectById(id);
        if (entity == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }
        return entity;
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(TicketUpdateDTO dto) {
        Long operatorId = SecurityContextUtils.currentUserIdRequired();
        TicketInfo existing = loadEntity(dto.getId());

        // 权限：仅创建人或管理员
        ensureCreatorOrAdmin(existing, operatorId);

        boolean changed = false;
        if (StringUtils.hasText(dto.getTitle()) && !dto.getTitle().equals(existing.getTitle())) {
            existing.setTitle(dto.getTitle());
            changed = true;
        }
        if (StringUtils.hasText(dto.getContent()) && !dto.getContent().equals(existing.getContent())) {
            existing.setContent(dto.getContent());
            changed = true;
        }
        if (!changed) {
            return; // 无变化跳过 update，避免无谓的 SQL + ticket_log
        }
        ticketInfoMapper.updateById(existing);

        // 同步 ticket_log(UPDATED)
        TicketLog log = new TicketLog();
        log.setTicketId(existing.getId());
        log.setEventType(TicketEventType.UPDATED);
        log.setOperatorId(operatorId);
        log.setContent(buildUpdatedLogContent(dto, existing));
        log.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(log);

        this.log.info("更新工单: ticketId={}, operatorId={}", existing.getId(), operatorId);
    }

    // ---------- 软删 ----------

    @Override
    @Transactional
    public void softDelete(Long id) {
        Long operatorId = SecurityContextUtils.currentUserIdRequired();
        TicketInfo existing = loadEntity(id);
        ensureCreatorOrAdmin(existing, operatorId);

        // 软删 —— 走 MP @TableLogic 的逻辑删除路径（{@code deleteById} 会把
        // {@code is_deleted} 字段从 {@code value} 改成 {@code delval}）。
        // 注意不能直接用 {@code updateById(existing)} + {@code setIsDeleted(1)}：
        // MP 在 {@code updateById} 路径会主动把 {@code @TableLogic} 字段从 SET 子句里剔除，
        // 静默丢掉软删意图 —— 这是 MP 防止"误清逻辑删除标记"的设计。
        int affected = ticketInfoMapper.deleteById(existing.getId());
        if (affected == 0) {
            // 并发场景：被并发删了 / 被并发修改过
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND, "工单已被其他操作修改，请刷新后重试");
        }

        this.log.info("软删工单: ticketId={}, operatorId={}", existing.getId(), operatorId);
    }

    // ---------- 内部辅助 ----------

    /**
     * 仅创建人或管理员 —— 用 {@code role:admin} 字符串判定"管理员"。
     * <p>
     * 当前 ticket 05 通过 SecurityContextUtils.hasAuthority(ADMIN_ROLE_KEY) 判定；
     * 后续 ticket 06+ 引入更细粒度权限（如 {@code ticket:manage}）时再重构。
     */
    private void ensureCreatorOrAdmin(TicketInfo ticket, Long operatorId) {
        if (ticket.getCreatorId() != null && ticket.getCreatorId().equals(operatorId)) {
            return;
        }
        if (SecurityContextUtils.hasAuthority(ADMIN_ROLE_KEY)) {
            return;
        }
        throw BusinessException.of(BusinessExceptionCode.AUTH_FORBIDDEN,
                "仅工单创建人或管理员可以操作该工单");
    }

    /**
     * Entity → VO。
     * <p>
     * 当前 ticket 05 暂不 JOIN sys_user —— 简化实现。
     * 拼装昵称由 ticket 06 引入 user-service 时再补（spec / ticket 06 范围）。
     */
    private TicketVO toVO(TicketInfo entity) {
        TicketVO vo = new TicketVO();
        vo.setId(entity.getId());
        vo.setTicketNo(entity.getTicketNo());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setType(entity.getType());
        vo.setPriority(entity.getPriority());
        vo.setStatus(entity.getStatus());
        vo.setCreatorId(entity.getCreatorId());
        vo.setHandlerId(entity.getHandlerId());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        // creatorName / handlerName 留空 —— ticket 06 JOIN sys_user 后补
        return vo;
    }

    /** ticket_log(CREATED) 的 content 文本 */
    private String buildCreatedLogContent(TicketInfo ticket) {
        StringBuilder sb = new StringBuilder();
        sb.append("title=").append(ticket.getTitle());
        sb.append(", type=").append(ticket.getType());
        sb.append(", priority=").append(ticket.getPriority());
        return sb.toString();
    }

    /** ticket_log(UPDATED) 的 content 文本 —— 记录改动的字段 */
    private String buildUpdatedLogContent(TicketUpdateDTO dto, TicketInfo after) {
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(dto.getTitle())) {
            sb.append("title=").append(after.getTitle());
        }
        if (StringUtils.hasText(dto.getContent())) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append("content.len=").append(after.getContent() == null ? 0 : after.getContent().length());
        }
        return sb.toString();
    }

}
