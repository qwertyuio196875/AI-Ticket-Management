package com.ticket.web.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.result.Result;
import com.ticket.system.dto.SysRoleAssignMenusDTO;
import com.ticket.system.dto.SysRoleSaveDTO;
import com.ticket.system.entity.SysRole;
import com.ticket.system.service.SysRoleService;
import com.ticket.web.system.vo.PageVO;
import com.ticket.web.system.vo.SysRoleVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口（ticket 03）。
 * <ul>
 *     <li>{@code GET    /api/v1/roles}：分页</li>
 *     <li>{@code GET    /api/v1/roles/all}：下拉框用全量</li>
 *     <li>{@code GET    /api/v1/roles/{id}}：详情</li>
 *     <li>{@code POST   /api/v1/roles}：创建</li>
 *     <li>{@code PUT    /api/v1/roles}：更新</li>
 *     <li>{@code DELETE /api/v1/roles/{id}}：删除（admin 角色不允许删）</li>
 *     <li>{@code GET    /api/v1/roles/{id}/menus}：取角色挂的菜单 id 列表</li>
 *     <li>{@code PUT    /api/v1/roles/{id}/menus}：分配菜单</li>
 * </ul>
 * 全部接口需 {@code role:manage} 权限。
 */
@RestController
@RequestMapping("/api/v1/roles")
@PreAuthorize("hasAuthority('role:manage')")
public class SysRoleController {

    private final SysRoleService sysRoleService;

    public SysRoleController(SysRoleService sysRoleService) {
        this.sysRoleService = sysRoleService;
    }

    @GetMapping
    public Result<PageVO<SysRoleVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        IPage<SysRole> page = sysRoleService.page(keyword, pageNum, pageSize);
        List<SysRoleVO> records = page.getRecords().stream().map(SysRoleVO::from).toList();
        return Result.success(new PageVO<>(page.getTotal(), pageNum, pageSize, records));
    }

    @GetMapping("/all")
    public Result<List<SysRoleVO>> listAll() {
        return Result.success(sysRoleService.listAll().stream().map(SysRoleVO::from).toList());
    }

    @GetMapping("/{id}")
    public Result<SysRoleVO> getById(@PathVariable Long id) {
        return Result.success(SysRoleVO.from(sysRoleService.getById(id)));
    }

    @PostMapping
    public Result<Long> create(@Valid @RequestBody SysRoleSaveDTO dto) {
        return Result.success(sysRoleService.create(dto));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody SysRoleSaveDTO dto) {
        sysRoleService.update(dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysRoleService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/{id}/menus")
    public Result<List<Long>> listMenuIds(@PathVariable Long id) {
        return Result.success(sysRoleService.listMenuIds(id));
    }

    @PutMapping("/{id}/menus")
    public Result<Void> assignMenus(@PathVariable Long id,
                                    @Valid @RequestBody SysRoleAssignMenusDTO dto) {
        sysRoleService.assignMenus(id, dto);
        return Result.success(null);
    }
}