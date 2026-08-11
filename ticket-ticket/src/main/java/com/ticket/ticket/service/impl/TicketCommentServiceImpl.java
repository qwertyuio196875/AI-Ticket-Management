package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.ticket.dto.TicketCommentCreateDTO;
import com.ticket.ticket.entity.TicketComment;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.CommentType;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketCommentMapper;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.service.TicketCommentService;
import com.ticket.ticket.vo.TicketCommentVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link TicketCommentService} 实现（ticket 07）。
 * <p>
 * <b>事务边界</b>：{@link #add} 标了 {@code @Transactional}，{@code ticket_comment}
 * 与 {@code ticket_log} 走同一事务（ticket 07 AC）。
 * <p>
 * <b>XSS 防护</b>：{@link #escapeHtml(String)} 在写入前把 {@code < / > / " / ' / &}
 * 等 HTML 特殊字符替换为实体（{@code &lt; &gt; &quot; &#x27; &amp;}）。
 * 不引入 jsoup / owasp-esapi —— 纯 static 方法足够覆盖 ticket 07 AC 范围。
 * <p>
 * <b>INTERNAL 可见性</b>：{@link #list} 按当前用户是否拥有 {@code admin} 权限
 * 决定是否隐藏 INTERNAL 评论。spec "INTERNAL comments filtered out for users
 * not in admin/internal-staff role" 在 ticket 03 已落地角色体系下，
 * 内部角色用 {@code admin} 单一字符串标识（ticket 07 范围内不引入更细粒度的
 * internal-staff 权限，避免范围蔓延）。
 * <p>
 * <b>不做的</b>（YAGNI）：
 * <ul>
 *     <li>评论无限层嵌套深度校验 —— UI 自行控制</li>
 *     <li>评论 Redis 缓存 —— ticket 09 详情缓存会覆盖</li>
 *     <li>评论级 @PreAuthorize 细分（创建者 / 管理员） —— 放在 Service 层
 *         {@link #delete} 内做字符串判定，依赖 {@code hasAuthority("admin")}</li>
 * </ul>
 */
@Service
public class TicketCommentServiceImpl implements TicketCommentService {

    private static final Logger log = LoggerFactory.getLogger(TicketCommentServiceImpl.class);

    /** ticket 06 沿用的"管理员"权限字符串 —— 单一字符串，无多 admin 角色 */
    private static final String ADMIN_AUTHORITY = "admin";

    private final TicketCommentMapper ticketCommentMapper;
    private final TicketInfoMapper ticketInfoMapper;
    private final TicketLogMapper ticketLogMapper;
    private final SysUserMapper sysUserMapper;

    public TicketCommentServiceImpl(TicketCommentMapper ticketCommentMapper,
                                    TicketInfoMapper ticketInfoMapper,
                                    TicketLogMapper ticketLogMapper,
                                    SysUserMapper sysUserMapper) {
        this.ticketCommentMapper = ticketCommentMapper;
        this.ticketInfoMapper = ticketInfoMapper;
        this.ticketLogMapper = ticketLogMapper;
        this.sysUserMapper = sysUserMapper;
    }

    // ---------- add ----------

    @Override
    @Transactional
    public Long add(Long ticketId, TicketCommentCreateDTO dto, Long operatorId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        if (dto == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "评论参数不能为空");
        }
        if (dto.getCommentType() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "评论类型不能为空");
        }
        if (!StringUtils.hasText(dto.getContent())) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "评论内容不能为空");
        }
        if (dto.getContent().length() > 2000) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "评论内容长度不能超过 2000 字符");
        }

        // 1. 工单存在性 + 未软删 + 非 CLOSED
        TicketInfo ticket = ticketInfoMapper.selectById(ticketId);
        if (ticket == null) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_NOT_FOUND);
        }
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw BusinessException.of(BusinessExceptionCode.TICKET_CLOSED);
        }

        // 2. 父评论校验（仅当 parentId 非空时）
        if (dto.getParentId() != null) {
            TicketComment parent = ticketCommentMapper.selectById(dto.getParentId());
            if (parent == null || !Objects.equals(parent.getTicketId(), ticketId)) {
                throw BusinessException.of(BusinessExceptionCode.COMMENT_PARENT_INVALID);
            }
        }

        // 3. 写入（content 已 escape）
        TicketComment comment = new TicketComment();
        comment.setTicketId(ticketId);
        comment.setContent(escapeHtml(dto.getContent()));
        comment.setCommentType(dto.getCommentType());
        comment.setCreatorId(operatorId);
        comment.setParentId(dto.getParentId());
        comment.setCreateTime(LocalDateTime.now());
        comment.setIsDeleted(TicketComment.NOT_DELETED);
        ticketCommentMapper.insert(comment);

        // 4. 同事务写 ticket_log(COMMENTED)
        TicketLog ticketLog = new TicketLog();
        ticketLog.setTicketId(ticketId);
        ticketLog.setEventType(TicketEventType.COMMENTED);
        ticketLog.setOperatorId(operatorId);
        ticketLog.setContent(buildCommentedLogContent(comment, dto.getParentId()));
        ticketLog.setCreateTime(LocalDateTime.now());
        ticketLogMapper.insert(ticketLog);

        log.info("新增工单评论: ticketId={}, commentId={}, commentType={}, operatorId={}",
                ticketId, comment.getId(), comment.getCommentType(), operatorId);
        return comment.getId();
    }

    // ---------- list ----------

    @Override
    public List<TicketCommentVO> list(Long ticketId) {
        if (ticketId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id 不能为空");
        }
        // 软删过滤由 @TableLogic 自动加 is_deleted = 0
        LambdaQueryWrapper<TicketComment> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketComment::getTicketId, ticketId)
                .orderByAsc(TicketComment::getCreateTime);
        List<TicketComment> rows = ticketCommentMapper.selectList(wrapper);
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }

        // INTERNAL 可见性过滤：当前用户无 admin 权限则隐藏 INTERNAL 评论
        boolean canSeeInternal = SecurityContextUtils.hasAuthority(ADMIN_AUTHORITY);
        List<TicketComment> visible = canSeeInternal
                ? rows
                : rows.stream()
                        .filter(c -> c.getCommentType() != CommentType.INTERNAL)
                        .collect(Collectors.toList());

        // 批量拼装 creatorName（避免 N+1）
        Set<Long> creatorIds = visible.stream()
                .map(TicketComment::getCreatorId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> nicknameMap = loadNicknames(creatorIds);

        return visible.stream().map(c -> toVO(c, nicknameMap)).collect(Collectors.toList());
    }

    // ---------- delete ----------

    @Override
    @Transactional
    public void delete(Long ticketId, Long commentId, Long operatorId) {
        if (ticketId == null || commentId == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "工单 id / 评论 id 不能为空");
        }
        TicketComment existing = ticketCommentMapper.selectById(commentId);
        if (existing == null || !Objects.equals(existing.getTicketId(), ticketId)) {
            throw BusinessException.of(BusinessExceptionCode.COMMENT_NOT_FOUND);
        }
        // 权限：仅创建者本人或管理员
        boolean isCreator = existing.getCreatorId() != null
                && existing.getCreatorId().equals(operatorId);
        boolean isAdmin = SecurityContextUtils.hasAuthority(ADMIN_AUTHORITY);
        if (!isCreator && !isAdmin) {
            throw BusinessException.of(BusinessExceptionCode.AUTH_FORBIDDEN,
                    "仅创建者本人或管理员可以删除该评论");
        }
        // 走 MP @TableLogic 的逻辑删除路径
        int affected = ticketCommentMapper.deleteById(commentId);
        if (affected == 0) {
            throw BusinessException.of(BusinessExceptionCode.COMMENT_NOT_FOUND,
                    "评论已被其他操作修改，请刷新后重试");
        }
        log.info("软删工单评论: ticketId={}, commentId={}, operatorId={}", ticketId, commentId, operatorId);
    }

    // ---------- 内部辅助 ----------

    /**
     * HTML 特殊字符转义 —— 入库前完成。
     * <p>
     * 覆盖 {@code & < > " '} 五个字符。覆盖范围对齐 ticket 07 AC
     * "Content field XSS-protected (HTML escape) before persisting"。
     * 不引入 jsoup —— 业务侧只需挡 XSS，5 个字符足够。
     */
    static String escapeHtml(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            switch (ch) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#x27;");
                    break;
                default:
                    sb.append(ch);
            }
        }
        return sb.toString();
    }

    private TicketCommentVO toVO(TicketComment entity, Map<Long, String> nicknameMap) {
        TicketCommentVO vo = new TicketCommentVO();
        vo.setId(entity.getId());
        vo.setTicketId(entity.getTicketId());
        vo.setContent(entity.getContent());
        vo.setCommentType(entity.getCommentType());
        vo.setCreatorId(entity.getCreatorId());
        vo.setCreatorName(nicknameMap.getOrDefault(entity.getCreatorId(), ""));
        vo.setParentId(entity.getParentId());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    /**
     * 批量加载创建人昵称，避免 N+1 SQL。
     */
    private Map<Long, String> loadNicknames(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(SysUser::getId, userIds);
        List<SysUser> users = sysUserMapper.selectList(wrapper);
        Map<Long, String> map = new HashMap<>(users.size() * 2);
        for (SysUser u : users) {
            map.put(u.getId(), u.getNickname() == null ? "" : u.getNickname());
        }
        return map;
    }

    /** COMMENTED 事件 content —— 记评论 id / 类型 / 父评论 id */
    private String buildCommentedLogContent(TicketComment comment, Long parentId) {
        StringBuilder sb = new StringBuilder();
        sb.append("commentId=").append(comment.getId());
        sb.append(", commentType=").append(comment.getCommentType());
        if (parentId != null) {
            sb.append(", parentId=").append(parentId);
        }
        return sb.toString();
    }
}
