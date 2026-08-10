package com.ticket.web.ticket;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.result.Result;
import com.ticket.ticket.aspect.OperationLog;
import com.ticket.ticket.dto.TicketCreateDTO;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.dto.TicketUpdateDTO;
import com.ticket.ticket.service.TicketInfoService;
import com.ticket.ticket.vo.TicketVO;
import com.ticket.web.system.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工单 CRUD 接口（ticket 05）。
 * <ul>
 *     <li>{@code POST   /api/v1/tickets}     —— 创建（需 {@code ticket:create}）</li>
 *     <li>{@code GET    /api/v1/tickets}     —— 分页列表（需 {@code ticket:view}）</li>
 *     <li>{@code GET    /api/v1/tickets/{id}}—— 详情（需 {@code ticket:view}）</li>
 *     <li>{@code PUT    /api/v1/tickets/{id}}—— 更新 title / content（需 {@code ticket:view}；服务层判创建人或管理员）</li>
 *     <li>{@code DELETE /api/v1/tickets/{id}}—— 软删（需 {@code ticket:delete}）</li>
 * </ul>
 * <p>
 * 所有写端点统一加 {@link OperationLog}，由 {@code OperationLogAspect} 自动落审计。
 * <p>
 * <b>不在本 Controller 的范围</b>：状态机迁移 / 分配 / 关闭（ticket 06）、
 * 评论（ticket 07）、AI 回复（ticket 08）、详情缓存（ticket 09）、
 * 导出（ticket 10）。
 */
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {

    private final TicketInfoService ticketInfoService;

    public TicketController(TicketInfoService ticketInfoService) {
        this.ticketInfoService = ticketInfoService;
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
}
