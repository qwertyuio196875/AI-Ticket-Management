package com.ticket.web.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.result.Result;
import com.ticket.system.dto.SysUserAssignRolesDTO;
import com.ticket.system.dto.SysUserSaveDTO;
import com.ticket.system.entity.SysUser;
import com.ticket.system.service.SysUserService;
import com.ticket.web.system.vo.PageVO;
import com.ticket.web.system.vo.SysUserVO;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户管理接口（ticket 03）。
 * <ul>
 *     <li>{@code GET    /api/v1/users}：分页查询（支持 keyword / status 过滤）</li>
 *     <li>{@code GET    /api/v1/users/{id}}：详情</li>
 *     <li>{@code GET    /api/v1/users/{id}/roles}：取当前用户角色 id 列表</li>
 *     <li>{@code POST   /api/v1/users}：创建</li>
 *     <li>{@code PUT    /api/v1/users}：更新</li>
 *     <li>{@code DELETE /api/v1/users/{id}}：删除</li>
 *     <li>{@code PUT    /api/v1/users/{id}/roles}：分配角色</li>
 * </ul>
 * 所有写操作均需 {@code user:manage} 权限 —— 通过 {@link PreAuthorize} 在方法边界拦截。
 */
@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasAuthority('user:manage')")
@Tag(name = "system", description = "系统管理：用户 / 角色 / 菜单 / 字典 / 工单分类")
public class SysUserController {

    private final SysUserService sysUserService;

    public SysUserController(SysUserService sysUserService) {
        this.sysUserService = sysUserService;
    }

    @GetMapping
    @Operation(summary = "分页查询用户")
    public Result<PageVO<SysUserVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        IPage<SysUser> page = sysUserService.page(keyword, status, pageNum, pageSize);
        List<SysUserVO> records = page.getRecords().stream().map(SysUserVO::from).toList();
        return Result.success(new PageVO<>(page.getTotal(), pageNum, pageSize, records));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 查询用户详情")
    public Result<SysUserVO> getById(@PathVariable Long id) {
        return Result.success(SysUserVO.from(sysUserService.getById(id)));
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public Result<Long> create(@Valid @RequestBody SysUserSaveDTO dto) {
        return Result.success(sysUserService.create(dto));
    }

    @PutMapping
    @Operation(summary = "更新用户")
    public Result<Void> update(@Valid @RequestBody SysUserSaveDTO dto) {
        sysUserService.update(dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.delete(id);
        return Result.success(null);
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "查询用户的角色 id 列表")
    public Result<List<Long>> listRoleIds(@PathVariable Long id) {
        return Result.success(sysUserService.listRoleIds(id));
    }

    @PutMapping("/{id}/roles")
    @Operation(summary = "为用户分配角色")
    public Result<Void> assignRoles(@PathVariable Long id,
                                    @Valid @RequestBody SysUserAssignRolesDTO dto) {
        sysUserService.assignRoles(id, dto);
        return Result.success(null);
    }
}