package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.TicketCategorySaveDTO;
import com.ticket.system.entity.TicketCategory;
import com.ticket.system.mapper.TicketCategoryMapper;
import com.ticket.system.service.TicketCategoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * {@link TicketCategoryService} 实现（ticket 04）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>name 唯一索引交给数据库兜底，捕获 {@link DuplicateKeyException}
 *         转 {@code CATEGORY_DUPLICATE}，不在前置 SELECT 探测</li>
 *     <li>status 为 null 时默认 ENABLED</li>
 * </ul>
 *
 * <p><b>TODO(code-review ticket4):</b> 本类与 {@link SysDictServiceImpl}
 * 的 {@code getById / create / update / delete / toEntity} 结构平行，目前只两处出现。
 * 第三次出现（如 ticket N 引入第三张配置表）时再抽基类或公共 mixin，
 * 当前 YAGNI 暂不抽象。
 */
@Service
public class TicketCategoryServiceImpl implements TicketCategoryService {

    private static final Logger log = LoggerFactory.getLogger(TicketCategoryServiceImpl.class);

    private final TicketCategoryMapper categoryMapper;

    public TicketCategoryServiceImpl(TicketCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    // ---------- 查询 ----------

    @Override
    public List<TicketCategory> listAllEnabled() {
        LambdaQueryWrapper<TicketCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketCategory::getStatus, TicketCategory.STATUS_ENABLED)
                .orderByAsc(TicketCategory::getSort)
                .orderByAsc(TicketCategory::getId);
        return categoryMapper.selectList(wrapper);
    }

    @Override
    public TicketCategory getById(Long id) {
        TicketCategory category = categoryMapper.selectById(id);
        if (category == null) {
            throw BusinessException.of(BusinessExceptionCode.CATEGORY_NOT_FOUND);
        }
        return category;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(TicketCategorySaveDTO dto) {
        TicketCategory category = toEntity(dto, null);
        try {
            categoryMapper.insert(category);
        } catch (DuplicateKeyException ex) {
            log.warn("创建工单分类失败 —— name 冲突: name={}", dto.getName());
            throw BusinessException.of(BusinessExceptionCode.CATEGORY_DUPLICATE);
        }
        log.info("创建工单分类: categoryId={}, name={}", category.getId(), category.getName());
        return category.getId();
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(TicketCategorySaveDTO dto) {
        if (dto.getId() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "更新工单分类时 id 不能为空");
        }
        TicketCategory existing = getById(dto.getId());
        TicketCategory merged = toEntity(dto, existing);
        try {
            categoryMapper.updateById(merged);
        } catch (DuplicateKeyException ex) {
            log.warn("更新工单分类失败 —— name 冲突: id={}", dto.getId());
            throw BusinessException.of(BusinessExceptionCode.CATEGORY_DUPLICATE);
        }
        log.info("更新工单分类: categoryId={}", merged.getId());
    }

    // ---------- 删除 ----------

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        categoryMapper.deleteById(id);
        log.info("删除工单分类: categoryId={}", id);
    }

    // ---------- 内部辅助 ----------

    /**
     * 将 DTO 合并到 entity。
     * <p>
     * 创建时：新建 entity，status 为 null 默认 ENABLED。
     * 更新时：只把 DTO 中非空字段合并进去，保留原有值。
     */
    private TicketCategory toEntity(TicketCategorySaveDTO dto, TicketCategory existing) {
        TicketCategory category = existing == null ? new TicketCategory() : existing;
        if (dto.getName() != null) {
            category.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            category.setDescription(dto.getDescription());
        }
        if (dto.getSort() != null) {
            category.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            category.setStatus(dto.getStatus());
        } else if (existing == null) {
            category.setStatus(TicketCategory.STATUS_ENABLED);
        }
        return category;
    }
}
