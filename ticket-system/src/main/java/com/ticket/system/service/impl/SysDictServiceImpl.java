package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysDictSaveDTO;
import com.ticket.system.entity.SysDict;
import com.ticket.system.mapper.SysDictMapper;
import com.ticket.system.service.SysDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * {@link SysDictService} 实现（ticket 04）。
 * <p>
 * 设计要点：
 * <ul>
 *     <li>(dict_type, dict_value) 唯一约束交给数据库兜底，捕获 {@link DuplicateKeyException}
 *         转 {@code DICT_DUPLICATE}，不在前置 SELECT 探测</li>
 *     <li>更新时 dictType / dictValue 不允许修改，只允许改 dictLabel / sort / status / remark</li>
 *     <li>listByType：type 为 null/空白时直接返回空列表，不查库</li>
 * </ul>
 *
 * <p><b>TODO(code-review ticket4):</b> 本类与 {@link TicketCategoryServiceImpl}
 * 的 {@code getById / create / update / delete / toEntity} 结构平行，目前只两处出现。
 * 第三次出现（如 ticket N 引入第三张配置表）时再抽基类或公共 mixin，
 * 当前 YAGNI 暂不抽象。
 */
@Service
public class SysDictServiceImpl implements SysDictService {

    private static final Logger log = LoggerFactory.getLogger(SysDictServiceImpl.class);

    private final SysDictMapper dictMapper;

    public SysDictServiceImpl(SysDictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    // ---------- 查询 ----------

    @Override
    public List<SysDict> listByType(String dictType) {
        if (!StringUtils.hasText(dictType)) {
            return List.of();
        }
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getDictType, dictType)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId);
        return dictMapper.selectList(wrapper);
    }

    @Override
    public List<SysDict> listAllEnabled() {
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysDict::getStatus, SysDict.STATUS_ENABLED)
                .orderByAsc(SysDict::getSort)
                .orderByAsc(SysDict::getId);
        return dictMapper.selectList(wrapper);
    }

    @Override
    public IPage<SysDict> page(String dictType, long pageNum, long pageSize) {
        Page<SysDict> page = Page.of(pageNum, pageSize);
        LambdaQueryWrapper<SysDict> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dictType)) {
            wrapper.eq(SysDict::getDictType, dictType);
        }
        wrapper.orderByAsc(SysDict::getSort).orderByAsc(SysDict::getId);
        return dictMapper.selectPage(page, wrapper);
    }

    @Override
    public SysDict getById(Long id) {
        SysDict dict = dictMapper.selectById(id);
        if (dict == null) {
            throw BusinessException.of(BusinessExceptionCode.DICT_NOT_FOUND);
        }
        return dict;
    }

    // ---------- 创建 ----------

    @Override
    @Transactional
    public Long create(SysDictSaveDTO dto) {
        SysDict dict = toEntity(dto, null);
        try {
            dictMapper.insert(dict);
        } catch (DuplicateKeyException ex) {
            log.warn("创建字典失败 —— dict_type + dict_value 冲突: dictType={}, dictValue={}",
                    dto.getDictType(), dto.getDictValue());
            throw BusinessException.of(BusinessExceptionCode.DICT_DUPLICATE);
        }
        log.info("创建字典: dictId={}, dictType={}, dictValue={}",
                dict.getId(), dict.getDictType(), dict.getDictValue());
        return dict.getId();
    }

    // ---------- 更新 ----------

    @Override
    @Transactional
    public void update(SysDictSaveDTO dto) {
        if (dto.getId() == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "更新字典时 id 不能为空");
        }
        SysDict existing = getById(dto.getId());
        SysDict merged = toEntity(dto, existing);
        try {
            dictMapper.updateById(merged);
        } catch (DuplicateKeyException ex) {
            log.warn("更新字典失败 —— dict_type + dict_value 冲突: id={}", dto.getId());
            throw BusinessException.of(BusinessExceptionCode.DICT_DUPLICATE);
        }
        log.info("更新字典: dictId={}", merged.getId());
    }

    // ---------- 删除 ----------

    @Override
    @Transactional
    public void delete(Long id) {
        getById(id);
        dictMapper.deleteById(id);
        log.info("删除字典: dictId={}", id);
    }

    // ---------- 内部辅助 ----------

    /**
     * 将 DTO 合并到 entity。
     * <p>
     * 创建时：新建 entity，status 为 null 默认 ENABLED。
     * 更新时：保留 existing 的 dictType / dictValue（不可修改），
     * 只把 DTO 中非空字段合并进去。
     */
    private SysDict toEntity(SysDictSaveDTO dto, SysDict existing) {
        SysDict dict = existing == null ? new SysDict() : existing;
        if (dto.getDictLabel() != null) {
            dict.setDictLabel(dto.getDictLabel());
        }
        if (dto.getSort() != null) {
            dict.setSort(dto.getSort());
        }
        if (dto.getStatus() != null) {
            dict.setStatus(dto.getStatus());
        } else if (existing == null) {
            dict.setStatus(SysDict.STATUS_ENABLED);
        }
        if (dto.getRemark() != null) {
            dict.setRemark(dto.getRemark());
        }
        // dictType / dictValue 只在创建时设置，更新时不允许改
        if (existing == null) {
            dict.setDictType(dto.getDictType());
            dict.setDictValue(dto.getDictValue());
        }
        return dict;
    }
}
