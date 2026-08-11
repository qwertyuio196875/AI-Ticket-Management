package com.ticket.web.ticket;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.result.Result;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.aspect.OperationLog;
import com.ticket.ticket.dto.TicketAssignDTO;
import com.ticket.ticket.dto.TicketCreateDTO;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.dto.TicketStatusChangeDTO;
import com.ticket.ticket.dto.TicketUpdateDTO;
import com.ticket.ticket.service.TicketInfoService;
import com.ticket.ticket.service.TicketStatusService;
import com.ticket.ticket.vo.TicketVO;
import com.ticket.web.system.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单 CRUD 接口（ticket 05）+ 状态机接口（ticket 06）。
 * <ul>
 *     <li>{@code POST   /api/v1/tickets}                  —— 创建（需 {@code ticket:create}）</li>
 *     <li>{@code GET    /api/v1/tickets}                  —— 分页列表（需 {@code ticket:view}）</li>
 *     <li>{@code GET    /api/v1/tickets/{id}}             —— 详情（需 {@code ticket:view}）</li>
 *     <li>{@code PUT    /api/v1/tickets/{id}}             —— 更新 title / content（需 {@code ticket:view}；服务层判创建人或管理员）</li>
 *     <li>{@code DELETE /api/v1/tickets/{id}}             —— 软删（需 {@code ticket:delete}）</li>
 *     <li>{@code PATCH  /api/v1/tickets/{id}/status}      —— 状态变更（需 {@code ticket:update}）</li>
 *     <li>{@code PUT    /api/v1/tickets/{id}/assign}      —— 分配处理人（需 {@code ticket:assign}）</li>
 *     <li>{@code POST   /api/v1/tickets/{id}/close}       —— 关闭工单（需 {@code ticket:close}）</li>
 * </ul>
 * <p>
 * 所有写端点统一加 {@link OperationLog}，由 {@code OperationLogAspect} 自动落审计。
 * <p>
 * <b>关于 ticket 06 端点显式传 {@code operatorId}</b>：与 ticket 05
 * {@code TicketInfoService} 在 Service 内调 {@code SecurityContextUtils.currentUserIdRequired()}
 * 自取的写法不同，状态机端点把当前用户从 Controller 层显式传入 Service —— 这样
 * Service 方法签名只依赖 {@code Long operatorId} 参数，单测可以纯 Mockito 注入固定 id
 * （{@code TicketStatusServiceImplTest}），不需要把 {@code SecurityContextHolder} 整套
 * 桩起来。两种风格各有取舍，本 Controller 按端点一致性原则：新接口用显式传参。
 * <p>
 * <b>不在本 Controller 的范围</b>：评论（ticket 07）、AI 回复（ticket 08）、
 * 详情缓存（ticket 09）、导出（ticket 10）。
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketInfoService ticketInfoService;
    private final TicketStatusService ticketStatusService;

    public TicketController(TicketInfoService ticketInfoService,
                            TicketStatusService ticketStatusService) {
        this.ticketInfoService = ticketInfoService;
        this.ticketStatusService = ticketStatusService;
    }

    /**
     * 创建工单。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ticket:create')")
    @OperationLog(value = "创建工单", type = "TICKET")
    public Result<Long> create(@Valid @RequestBody TicketCreateDTO dto) {
        return Result.success(ticketInfoService.create(dto));
    }

    /**
     * 分页查询工单列表。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('ticket:view')")
    public Result<PageVO<TicketVO>> page(TicketQueryDTO query) {
        IPage<TicketVO> page = ticketInfoService.page(query);
        return Result.success(new PageVO<>(page.getTotal(),
                page.getCurrent(), page.getSize(), page.getRecords()));
    }

    /**
     * 查询工单详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('ticket:view')")
    public Result<TicketVO> getById(@PathVariable Long id) {
        return Result.success(ticketInfoService.getById(id));
    }

    /**
     * 更新工单 title / content。
     * <p>
     * 权限：依赖 Service 层"创建人或管理员"判定（{@code ticket:view} 仅放行到 Controller 边界，
     * 写权限由 Service 内 {@code ensureCreatorOrAdmin} 把关）。
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ticket:view')")
    @OperationLog(value = "更新工单", type = "TICKET")
    public Result<Void> update(@PathVariable Long id,
                               @Valid @RequestBody TicketUpdateDTO dto) {
        // path id 与 body id 取一即可；这里以 path 为准
        dto.setId(id);
        ticketInfoService.update(dto);
        return Result.success(null);
    }

    /**
     * 软删工单。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ticket:delete')")
    @OperationLog(value = "删除工单", type = "TICKET")
    public Result<Void> delete(@PathVariable Long id) {
        ticketInfoService.softDelete(id);
        return Result.success(null);
    }

    // ---------- ticket 06：状态机 ----------

    /**
     * 变更工单状态（ticket 06 AC）。
     * <p>
     * 走 {@link TicketStatusService#changeStatus} 唯一入口，
     * 由 Service 层通过 {@code TicketStatus.canTransitTo} 集中校验迁移合法性；
     * 非法迁移抛 {@code TICKET_INVALID_TRANSITION}（HTTP 409，{@code T0102}）。
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('ticket:update')")
    @OperationLog(value = "变更工单状态", type = "TICKET")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @Valid @RequestBody TicketStatusChangeDTO dto) {
        ticketStatusService.changeStatus(id, dto.getTargetStatus(), dto.getReason(),
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(null);
    }

    /**
     * 分配处理人（ticket 06 AC）。
     * <p>
     * 若工单当前状态为 PENDING，会同时触发 {@code PENDING → PROCESSING} 的状态迁移
     * （同事务写两条 {@code ticket_log}）。
     */
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAuthority('ticket:assign')")
    @OperationLog(value = "分配工单", type = "TICKET")
    public Result<Void> assign(@PathVariable Long id,
                               @Valid @RequestBody TicketAssignDTO dto) {
        ticketStatusService.assign(id, dto.getHandlerId(), dto.getReason(),
                SecurityContextUtils.currentUserIdRequired());
        return Result.success(null);
    }

    /**
     * 关闭工单（ticket 06 AC）。
     * <p>
     * 等价于 {@code PATCH /status}（target=CLOSED），但作为单独端点保留，
     * 便于前端按钮权限分组（{@code ticket:close}）与审计日志分类。
     */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('ticket:close')")
    @OperationLog(value = "关闭工单", type = "TICKET")
    public Result<Void> close(@PathVariable Long id) {
        ticketStatusService.close(id, null, SecurityContextUtils.currentUserIdRequired());
        return Result.success(null);
    }
}
