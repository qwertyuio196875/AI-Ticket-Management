package com.ticket.ticket.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.ticket.dto.TicketCreateDTO;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.dto.TicketUpdateDTO;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.vo.TicketVO;

/**
 * 工单主表 Service 接口（ticket 05）。
 * <p>
 * 端到端：{@code ticket_info} ↔ {@code ticket_log}（同事务），
 * 以及 {@code TicketVO} 展示拼装。
 * <p>
 * 后续 ticket 06 会在本接口上挂 {@code changeStatus / assign / close} 等状态机 API。
 */
public interface TicketInfoService {

    /**
     * 创建工单。
     * <p>
     * 流程：
     * <ol>
     *     <li>生成 ticket_no（{@link TicketNoGenerator}）</li>
     *     <li>从 SecurityContext 取当前用户 id 作为 {@code creatorId}</li>
     *     <li>默认 status = PENDING，handlerId = null</li>
     *     <li>同事务写 {@code ticket_log(event=CREATED)}（ADR-0012）</li>
     * </ol>
     *
     * @param dto 创建参数（title / content / type / priority）
     * @return 新工单 id
     */
    Long create(TicketCreateDTO dto);

    /**
     * 分页查询工单列表。
     * <p>
     * 过滤：{@code is_deleted = 0}（自动）+ DTO 内非空字段。
     * 返回 VO 列表（已 JOIN sys_user 拼装创建人昵称）。
     */
    IPage<TicketVO> page(TicketQueryDTO query);

    /**
     * 查询工单详情。
     *
     * @param id 工单 id
     * @return 详情 VO
     * @throws com.ticket.common.exception.BusinessException {@code TICKET_NOT_FOUND} 当 id 不存在或已软删
     */
    TicketVO getById(Long id);

    /**
     * 更新工单 title / content。
     * <p>
     * 权限：仅创建人或管理员可改（{@code ticket:manage}）。
     *
     * @param dto 更新参数
     */
    void update(TicketUpdateDTO dto);

    /**
     * 软删工单（{@code is_deleted = 1}）。
     * <p>
     * 列表查询自动过滤已软删的工单。
     * 后续 ticket 会把"仅创建人或管理员"加进来；当前 ticket 05 依赖
     * Controller 层 {@code @PreAuthorize("hasAuthority('ticket:delete')")}
     * 把权限压到管理员侧。
     *
     * @param id 工单 id
     */
    void softDelete(Long id);

    /**
     * 内部方法 —— 根据 id 加载 entity（不区分软删）。
     * <p>
     * Service 内部链路使用（如校验创建人），Controller 不直接调用。
     */
    TicketInfo loadEntity(Long id);
}
