package com.ticket.web.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.result.Result;
import com.ticket.system.dto.SysDictSaveDTO;
import com.ticket.system.entity.SysDict;
import com.ticket.system.service.SysDictService;
import com.ticket.web.system.vo.PageVO;
import com.ticket.web.system.vo.SysDictVO;
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
 * 数据字典管理接口（ticket 04）。
 * <ul>
 *     <li>{@code GET    /api/v1/dicts}：分页查询（需 dict:manage）</li>
 *     <li>{@code GET    /api/v1/dicts/{id}}：详情（需 dict:manage）</li>
 *     <li>{@code GET    /api/v1/dicts/type/{dictType}}：按类型查列表（所有已登录用户可访问）</li>
 *     <li>{@code POST   /api/v1/dicts}：创建（需 dict:manage）</li>
 *     <li>{@code PUT    /api/v1/dicts}：更新（需 dict:manage）</li>
 *     <li>{@code DELETE /api/v1/dicts/{id}}：删除（需 dict:manage）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/dicts")
@Tag(name = "system", description = "系统管理：用户 / 角色 / 菜单 / 字典 / 工单分类")
public class SysDictController {

    private final SysDictService sysDictService;

    public SysDictController(SysDictService sysDictService) {
        this.sysDictService = sysDictService;
    }

    /**
     * 分页查询（管理页面用）。
     */
    @GetMapping
    @PreAuthorize("hasAuthority('dict:manage')")
    @Operation(summary = "分页查询字典（管理页面用）")
    public Result<PageVO<SysDictVO>> page(
            @RequestParam(required = false) String dictType,
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "20") long pageSize) {
        IPage<SysDict> page = sysDictService.page(dictType, pageNum, pageSize);
        List<SysDictVO> records = page.getRecords().stream().map(SysDictVO::from).toList();
        return Result.success(new PageVO<>(page.getTotal(), pageNum, pageSize, records));
    }

    /**
     * 按 id 查询详情。
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:manage')")
    @Operation(summary = "按 id 查询字典详情")
    public Result<SysDictVO> getById(@PathVariable Long id) {
        return Result.success(SysDictVO.from(sysDictService.getById(id)));
    }

    /**
     * 按字典类型查列表 —— 前端下拉用，任何已登录用户可访问。
     */
    @GetMapping("/type/{dictType}")
    @Operation(summary = "按字典类型查列表（前端下拉用）")
    public Result<List<SysDictVO>> listByType(@PathVariable String dictType) {
        return Result.success(sysDictService.listByType(dictType).stream()
                .map(SysDictVO::from).toList());
    }

    /**
     * 创建字典条目。
     */
    @PostMapping
    @PreAuthorize("hasAuthority('dict:manage')")
    @Operation(summary = "创建字典条目")
    public Result<Long> create(@Valid @RequestBody SysDictSaveDTO dto) {
        return Result.success(sysDictService.create(dto));
    }

    /**
     * 更新字典条目。
     */
    @PutMapping
    @PreAuthorize("hasAuthority('dict:manage')")
    @Operation(summary = "更新字典条目")
    public Result<Void> update(@Valid @RequestBody SysDictSaveDTO dto) {
        sysDictService.update(dto);
        return Result.success(null);
    }

    /**
     * 删除字典条目。
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('dict:manage')")
    @Operation(summary = "删除字典条目")
    public Result<Void> delete(@PathVariable Long id) {
        sysDictService.delete(id);
        return Result.success(null);
    }
}
