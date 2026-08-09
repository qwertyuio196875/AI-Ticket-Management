package com.ticket.web.system;

import com.ticket.common.result.Result;
import com.ticket.security.user.LoginUser;
import com.ticket.system.dto.SysMenuSaveDTO;
import com.ticket.system.entity.SysMenu;
import com.ticket.system.service.SysMenuService;
import com.ticket.system.vo.SysMenuTreeVO;
import com.ticket.web.system.vo.PageVO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
 * 菜单管理接口（ticket 03）。
 * <ul>
 *     <li>{@code GET    /api/v1/menus}：全量菜单列表（管理页面用）</li>
 *     <li>{@code GET    /api/v1/menus/tree}：当前登录用户的菜单树（侧边栏用）</li>
 *     <li>{@code GET    /api/v1/menus/{id}}：详情</li>
 *     <li>{@code POST   /api/v1/menus}：创建</li>
 *     <li>{@code PUT    /api/v1/menus}：更新</li>
 *     <li>{@code DELETE /api/v1/menus/{id}}：删除（级联删子菜单）</li>
 * </ul>
 * 管理类接口需 {@code menu:manage}；{@code /tree} 例外 —— 它面向所有已登录用户，
 * 走自身权限过滤而非 {@code @PreAuthorize}。
 */
@RestController
@RequestMapping("/api/v1/menus")
public class SysMenuController {

    private final SysMenuService sysMenuService;

    public SysMenuController(SysMenuService sysMenuService) {
        this.sysMenuService = sysMenuService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('menu:manage')")
    public Result<List<SysMenu>> listAll() {
        return Result.success(sysMenuService.listAll());
    }

    /**
     * 当前用户可见的菜单树 —— 按角色过滤。
     * <p>
     * principal 由 {@code JwtAuthFilter} 写入 SecurityContext；
     * 从中取 {@code userId} 后走 {@code SysMenuService.treeByUser}。
     * <p>
     * 不加 {@code @PreAuthorize}：菜单可见性本身就是按"角色→菜单"过滤，
     * 即使没菜单权限也能拿到"自己有权访问的"那一部分（通常非空）。
     */
    @GetMapping("/tree")
    public Result<List<SysMenuTreeVO>> tree(@AuthenticationPrincipal LoginUser currentUser) {
        return Result.success(sysMenuService.treeByUser(currentUser.getUserId()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:manage')")
    public Result<SysMenu> getById(@PathVariable Long id) {
        return Result.success(sysMenuService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('menu:manage')")
    public Result<Long> create(@Valid @RequestBody SysMenuSaveDTO dto) {
        return Result.success(sysMenuService.create(dto));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('menu:manage')")
    public Result<Void> update(@Valid @RequestBody SysMenuSaveDTO dto) {
        sysMenuService.update(dto);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('menu:manage')")
    public Result<Void> delete(@PathVariable Long id) {
        sysMenuService.delete(id);
        return Result.success(null);
    }

    /** 仅用于占位 —— 保持 PageVO 引用，否则 IDE 警告 unused import */
    @SuppressWarnings("unused")
    private static <T> PageVO<T> emptyPage() {
        return new PageVO<>(0, 1, 0, List.of());
    }
}