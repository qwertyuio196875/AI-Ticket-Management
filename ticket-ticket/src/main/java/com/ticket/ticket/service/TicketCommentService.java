package com.ticket.ticket.service;

import com.ticket.ticket.dto.TicketCommentCreateDTO;
import com.ticket.ticket.vo.TicketCommentVO;

import java.util.List;

/**
 * 工单评论 Service（ticket 07，详见 ADR-0034）。
 * <p>
 * <b>职责边界</b>：所有 {@code ticket_comment} 写操作必须经本接口。Service 层负责：
 * <ul>
 *     <li>工单存在性 + 状态校验（CLOSED 不允许评论）</li>
 *     <li>父评论校验（parent_id 存在 + 同一工单）</li>
 *     <li>内容 XSS HTML escape（持久化前完成）</li>
 *     <li>写 {@code ticket_comment} + {@code ticket_log(event=COMMENTED)} 同事务（ticket 07 AC）</li>
 *     <li>列表查询时按当前用户角色过滤 INTERNAL 评论</li>
 *     <li>软删（{@code is_deleted = 1}），仅创建者本人或管理员可删</li>
 * </ul>
 * <p>
 * <b>事务边界</b>：每个写方法都标 {@code @Transactional}，保证 {@code ticket_comment}
 * + {@code ticket_log} 原子（ticket 07 AC；{@code ticket_info} 不参与评论事务，避免评论失败
 * 把工单主表回滚）。
 * <p>
 * <b>权限校验</b>：{@code @PreAuthorize("hasAuthority('ticket:comment')")} 放在
 * Controller 层；Service 层只做业务合法性判定（"仅创建者或管理员可删"）。
 */
public interface TicketCommentService {

    /**
     * 新增评论（ticket 07 AC）。
     * <p>
     * 流程：
     * <ol>
     *     <li>校验工单存在 + 未软删 + 状态非 CLOSED（不满足 → 抛 {@code T0101} / {@code T0103}）</li>
     *     <li>若 {@code parentId} 非空，校验父评论存在 + 同一工单
     *         （不满足 → 抛 {@code T0105}）</li>
     *     <li>{@code content} XSS HTML escape</li>
     *     <li>插入 {@code ticket_comment}，{@code createTime = now()}</li>
     *     <li>同事务追加 {@code ticket_log(event=COMMENTED)}（ticket 07 AC）</li>
     * </ol>
     *
     * @param ticketId   工单主键（来自 path）
     * @param dto        评论内容 / 类型 / 父评论
     * @param operatorId 当前登录用户 id（来自 SecurityContext）
     * @return 新评论 id
     */
    Long add(Long ticketId, TicketCommentCreateDTO dto, Long operatorId);

    /**
     * 获取工单评论列表（ticket 07 AC）。
     * <p>
     * 排序：{@code ORDER BY create_time ASC}；过滤：软删自动隐藏
     * （MP {@code @TableLogic}）。
     * <p>
     * <b>可见性</b>：INTERNAL 评论仅内部角色（admin / agent）可见
     * （ADR-0034 内部备注语义）。当前用户角色判定走
     * {@code SecurityContextUtils.hasAuthority("admin")} —— admin 可看全部，
     * 其他用户（agent 同样被视为内部可见）按 spec "INTERNAL comments filtered out
     * for users not in admin/internal-staff role" 解释。
     * <p>
     * <b>实现取舍</b>：本接口为 ticket 07 简化版，"internal-staff" 范围在 ticket 03
     * 角色体系下用 {@code admin} 单一权限字符串判定；后续若引入 agent 角色对应的
     * 内部可见权限（{@code ticket:comment:internal}），由 ticket 11+ 加扩展点。
     *
     * @param ticketId 工单主键
     * @return 评论列表（VO），按 create_time ASC
     */
    List<TicketCommentVO> list(Long ticketId);

    /**
     * 软删评论（ticket 07 AC）。
     * <p>
     * 权限：仅创建者本人或管理员（{@code hasAuthority("admin")}）。
     * 软删（{@code is_deleted = 1}），不真删。
     *
     * @param ticketId  工单主键
     * @param commentId 评论主键
     * @param operatorId 当前登录用户 id
     */
    void delete(Long ticketId, Long commentId, Long operatorId);
}
