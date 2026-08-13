package com.ticket.web.system;

import com.ticket.common.result.Result;
import com.ticket.system.dto.TicketCategorySaveDTO;
import com.ticket.system.entity.TicketCategory;
import com.ticket.system.service.TicketCategoryService;
import com.ticket.web.system.vo.TicketCategoryVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.util.List;

/**
 * 工单分类管理接口（ticket 04）。
 * <ul>
 *     <li>{@code GET    /api/v1/ticket-categories}：所有已启用分类列表（所有已登录用户可访问）</li>
 *     <li>{@code GET    /api/v1/ticket-categories/{id}}：详情（需 category:manage）</li>
 *     <li>{@code POST   /api/v1/ticket-categories}：创建（需 category:manage）</li>
 *     <li>{@code PUT    /api/v1/ticket-categories}：更新（需 category:manage）</li>
 *     <li>{@code DELETE /api/v1/ticket-categories/{id}}：删除（需 category:manage）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ticket-categories")
@Tag(name = "system", description = "系统管理：用户 / 角色 / 菜单 / 字典 / 工单分类")
public class TicketCategoryController {

    private final TicketCategoryService ticketCategoryService;

    public TicketCategoryController(TicketCategoryService ticketCategoryService) {
        this.ticketCategoryService = ticketCategoryService;
    }

    /**
     * 查询所有已启用分类 —— 前端下拉用，任何已登录用户可访问。
     */
    @GetMapping
    @Operation(summary = "查询所有已启用分类（下拉框用）")
    public Result<List<TicketCategoryVO>> listAllEnabled() {
        return Result.success(ticketCategoryService.listAllEnabled().stream()
                .map(TicketCategoryVO::from).toList());
    }

    /**
     * 按 id 查询详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "按 id 查询工单分类详情")
    public Result<TicketCategoryVO> getById(@PathVariable Long id) {
        return Result.success(TicketCategoryVO.from(ticketCategoryService.getById(id)));
    }

    /**
     * 创建工单分类。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "创建工单分类")
    public Result<Long> create(@Valid @RequestBody TicketCategorySaveDTO dto) {
        return Result.success(ticketCategoryService.create(dto));
    }

    /**
     * 更新工单分类。
     */
    @PutMapping
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "更新工单分类")
    public Result<Void> update(@Valid @RequestBody TicketCategorySaveDTO dto) {
        ticketCategoryService.update(dto);
        return Result.success(null);
    }

    /**
     * 删除工单分类。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('category:manage')")
    @Operation(summary = "删除工单分类")
    public Result<Void> delete(@PathVariable Long id) {
        ticketCategoryService.delete(id);
        return Result.success(null);
    }
}
