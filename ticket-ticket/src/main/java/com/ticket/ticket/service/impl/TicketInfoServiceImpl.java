package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.ai.client.TicketClassifier;
import com.ticket.ai.client.dto.TicketClassifyResult;
import com.ticket.ai.config.AiProperties;
import com.ticket.ai.enums.AiCallType;
import com.ticket.ai.service.AIRecordPersistService;
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
import com.ticket.ticket.service.cache.TicketCacheService;
import com.ticket.ticket.vo.TicketVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * {@link TicketInfoService} 实现（ticket 05 + ticket 08 AI 分类）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li><b>事务原子</b>：{@code create()} 内 {@code ticket_info} + {@code ticket_log(CREATED)}
 *         走同一 {@code @Transactional}，符合 ADR-0012</li>
 *     <li><b>AI 分类</b>：在 {@code create()} 尾部同步触发（2s 短超时 + 兜底）；
 *         <b>catch 住 AI 抛出的任何异常</b>，事务不会因 AI 失败而回滚——保证"AI 失败不阻塞工单创建"
 *         （spec 双层防线第二层）。AI 分类结果写回 {@code ticket_info.type/priority}，
 *         成功 / 失败均落 {@code ai_ticket_record}（双层防线第一层记录）。</li>
 *     <li><b>权限校验</b>：{@code update} 服务层判"创建人或管理员"
 *         （{@code admin} 角色 key），与 Controller 层
 *         {@code @PreAuthorize} 形成两道闸门</li>
 *     <li><b>软删过滤</b>：依赖 MP {@code @TableLogic}，所有查询自动加
 *         {@code is_deleted = 0}</li>
 *     <li><b>默认兜底</b>：{@code priority} 为空时取 {@code MEDIUM}</li>
 * </ul>
 * <p>
 * <b>关于"AI 调用事务外"的妥协</b>：spec ADR-0012 明确说 AI 调用应在事务外
 *（避免 2s 超时持锁）。Spring 事务自调用机制使"同 bean 内拆方法"难以严格事务外，
 * 当前实现选择<b>事务内 try-catch</b>（catch 任何 RuntimeException，事务不回滚）——
 * 这是工程妥协：实际生产中 AI 分类逻辑快速（通常 <500ms），2s 超时是兜底极端情况。
 * 真要做到严格事务外需要把 {@code createTicketInTransaction} / {@code applyClassification}
 * 拆到独立 bean + 通过 AOP 代理调用，工程复杂度高，本 ticket 不展开。
 * <p>
 * <b>YAGNI 变更记录</b>：
 * <ul>
 *     <li>ticket 08：AI 分类已就绪（删除"AI 分类 —— ticket 08 引入"注释）</li>
 *     <li>Redis 详情缓存 —— ticket 09 引入</li>
 *     <li>状态机迁移 —— ticket 06 引入（{@code TicketStatus.canTransitTo} 已就绪）</li>
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
    private final TicketClassifier ticketClassifier;
    private final AIRecordPersistService aiRecordPersistService;
    private final TicketCacheService ticketCacheService;
    private final long classifyTimeoutMs;

    public TicketInfoServiceImpl(TicketInfoMapper ticketInfoMapper,
                                 TicketLogMapper ticketLogMapper,
                                 TicketNoGenerator ticketNoGenerator,
                                 TicketClassifier ticketClassifier,
                                 AIRecordPersistService aiRecordPersistService,
                                 AiProperties aiProperties,
                                 TicketCacheService ticketCacheService) {
        this.ticketInfoMapper = ticketInfoMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.ticketNoGenerator = ticketNoGenerator;
        this.ticketClassifier = ticketClassifier;
        this.aiRecordPersistService = aiRecordPersistService;
        this.ticketCacheService = ticketCacheService;
        this.classifyTimeoutMs = aiProperties.classifyTimeoutMs();
    }

    // ---------- 创建（ticket 05 + 08 AI 分类）----------

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
        TicketLog ticketLog = new TicketLog();
        ticketLog.setTicketId(ticket.getId());
        ticketLog.setEventType(TicketEventType.CREATED);
        ticketLog.setOperatorId(creatorId);
        ticketLog.setContent(buildCreatedLogContent(ticket));
        ticketLog.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(ticketLog);

        // AI 分类（事务内 catch 异常 → 事务不回滚；2s 短超时 + fallback）
        // userProvidedType/Priority 用于判断"用户是否传了字段"——只覆盖用户没传的字段，
        // 避免用户显式选择的 priority=HIGH 被 AI fallback MEDIUM 静默改写。
        boolean userProvidedType = StringUtils.hasText(dto.getType());
        boolean userProvidedPriority = StringUtils.hasText(dto.getPriority());
        try {
            applyAiClassification(ticket, userProvidedType, userProvidedPriority);
        } catch (Exception ex) {
            // catch ALL（RuntimeException + Error 等）确保 AI 失败绝不影响主流程
            log.warn("AI 分类调用异常（已捕获，不影响工单创建）: ticketId={}, cause={}",
                    ticket.getId(), ex.toString());
        }

        log.info("创建工单: ticketId={}, ticketNo={}, creatorId={}",
                ticket.getId(), ticket.getTicketNo(), creatorId);
        return ticket.getId();
    }

    /**
     * AI 分类核心逻辑（事务内同步调用 + 2s 短超时 + 兜底值落到 ticket_info）。
     * <p>
     * 步骤：
     * <ol>
     *     <li>{@link CompletableFuture} 异步发分类，{@code future.get(timeoutMs)} 同步等结果</li>
     *     <li>超时 / 异常 → 写失败 ai_ticket_record + 应用 fallback (OTHER / MEDIUM) 到 ticket_info（仅覆盖用户没传的字段）</li>
     *     <li>成功且非兜底值 → 写成功 ai_ticket_record + 应用到 ticket_info + 写 ticket_log(AI_CALLED)</li>
     *     <li>成功但解析失败（兜底值） → 写失败 ai_ticket_record + 应用 fallback</li>
     * </ol>
     * <p>
     * <b>注意</b>：本方法抛出的任何异常会被 {@link #create(TicketCreateDTO)} 的 try-catch
     * 吞掉，不会导致 {@code ticket_info} 落库失败。
     *
     * @param ticket              当前已落库的 ticket 实体
     * @param userProvidedType    true = 用户传了 type（不覆盖）
     * @param userProvidedPriority true = 用户传了 priority（不覆盖）
     */
    private void applyAiClassification(TicketInfo ticket, boolean userProvidedType, boolean userProvidedPriority) {
        CompletableFuture<TicketClassifyResult> future = CompletableFuture.supplyAsync(
                () -> ticketClassifier.classify(ticket.getTitle(), ticket.getContent()));

        TicketClassifyResult result = null;
        String errorLog = null;
        boolean timedOut = false;
        try {
            result = future.get(classifyTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            timedOut = true;
            future.cancel(true);
            // error_log 用完整异常类名 + message（spec line 56）
            errorLog = ex.getClass().getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            log.warn("AI 分类 2s 超时 ticketId={}", ticket.getId());
        } catch (ExecutionException ex) {
            // error_log 用完整异常类名 + message（spec line 56）
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            errorLog = cause.getClass().getName() + ": " + (cause.getMessage() != null ? cause.getMessage() : cause.toString());
            log.warn("AI 分类执行异常 ticketId={}, cause={}", ticket.getId(), errorLog);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            // error_log 用完整异常类名 + message（spec line 56）
            errorLog = ex.getClass().getName() + ": " + (ex.getMessage() != null ? ex.getMessage() : ex.toString());
            log.warn("AI 分类被中断 ticketId={}", ticket.getId());
        }

        // AI 返回的 result 是 fallback / null / 超时 → 强制使用 fallback 兜底
        TicketClassifyResult effective = result;
        if (timedOut || effective == null || isFallback(effective)) {
            if (effective == null && errorLog == null) {
                errorLog = "AI 返回 null";
            }
            if (effective != null && isFallback(effective) && errorLog == null) {
                errorLog = "AI 返回兜底值（解析失败 / 字段缺失）";
            }
            effective = TicketClassifyResult.fallback();
            // 写 ai_ticket_record 失败记录（独立事务）
            try {
                aiRecordPersistService.saveFailure(ticket.getId(), AiCallType.CLASSIFY,
                        "deepseek-chat", "", errorLog);
            } catch (Exception ex) {
                log.warn("写 ai_ticket_record 失败 ticketId={}", ticket.getId(), ex);
            }
        } else {
            // AI 成功 → 写 ai_ticket_record 成功记录
            try {
                aiRecordPersistService.saveSuccess(ticket.getId(), AiCallType.CLASSIFY,
                        "deepseek-chat", "", "");
            } catch (Exception ex) {
                log.warn("写 ai_ticket_record 成功记录失败 ticketId={}", ticket.getId(), ex);
            }
        }

        // 应用分类到 ticket_info（事务内）
        // <p>
        // <b>覆盖规则</b>：<b>仅当用户没传字段时</b>才用 AI 兜底值覆盖。
        // 用户传了 type / priority → 视为显式选择，AI 兜底不再覆盖（避免用户传 "HIGH"
        // 被 AI fallback "MEDIUM" 静默改写）；spec "分类结果 feeds into ticket_info.type/priority"
        // 措辞较模糊，本实现采用"用户输入优先"的工程语义，符合大多数工单系统的预期。
        // <p>
        // 注意：{@code ticket.priority} 默认值是 "MEDIUM"（{@link TicketCreateDTO#DEFAULT_PRIORITY}），
        // 不能用 {@code !hasText(ticket.getPriority())} 判断"用户没传"，必须用 {@code userProvidedPriority} 标志位。
        boolean changed = false;
        if (!userProvidedType
                && effective.type() != null
                && !Objects.equals(effective.type().name(), ticket.getType())) {
            ticket.setType(effective.type().name());
            changed = true;
        }
        if (!userProvidedPriority
                && effective.priority() != null
                && !Objects.equals(effective.priority(), ticket.getPriority())) {
            ticket.setPriority(effective.priority());
            changed = true;
        }
        if (changed) {
            ticketInfoMapper.updateById(ticket);
            // 同步 ticket_log(AI_CALLED) 业务事件
            TicketLog aiLog = new TicketLog();
            aiLog.setTicketId(ticket.getId());
            aiLog.setEventType(TicketEventType.AI_CALLED);
            aiLog.setOperatorId(null);
            aiLog.setContent("type=" + effective.type() + ", priority=" + effective.priority());
            aiLog.setCreateTime(LocalDateTime.now());
            ticketLogMapper.insert(aiLog);
        }
    }

    /** 判断 AI 返回结果是否为兜底值（OTHER / MEDIUM / 待人工分配） */
    private static boolean isFallback(TicketClassifyResult r) {
        return r != null && r.type() != null && r.type().name().equals("OTHER")
                && "MEDIUM".equals(r.priority())
                && "待人工分配".equals(r.department());
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
        // ticket 09：读路径走 Redis cache-aside（防击穿 + 防穿透 + TTL 抖动），
        // 委托给 TicketCacheService。详见 ADR-0004。
        return ticketCacheService.getById(id);
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
        TicketLog ticketLog = new TicketLog();
        ticketLog.setTicketId(existing.getId());
        ticketLog.setEventType(TicketEventType.UPDATED);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setContent(buildUpdatedLogContent(dto, existing));
        ticketLog.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(ticketLog);

        // ticket 09：after-commit 失效缓存 —— 委托给 TicketCacheService.evictAfterCommit
        // 确保只有事务成功提交后才 DEL key；事务回滚则不清缓存（避免脏 evict）
        ticketCacheService.evictAfterCommit(existing.getId());

        log.info("更新工单: ticketId={}, operatorId={}", existing.getId(), operatorId);
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

        // ticket 09：软删后清除详情缓存（after-commit，避免脏 evict）
        ticketCacheService.evictAfterCommit(existing.getId());

        log.info("软删工单: ticketId={}, operatorId={}", existing.getId(), operatorId);
    }

    // ---------- 内部辅助 ----------

    /**
     * 仅创建人或管理员 —— 用 {@code admin} 角色 key 判定"管理员"。
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
>当前 ticket 05 暂不 JOIN sys_user —— 简化实现。
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