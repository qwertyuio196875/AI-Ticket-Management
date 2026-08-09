package com.ticket.system.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.system.dto.SysDictSaveDTO;
import com.ticket.system.entity.SysDict;

import java.util.List;

/**
 * 数据字典服务接口（ticket 04）。
 * <p>
 * 业务规则：
 * <ul>
 *     <li>dictType + dictValue 联合唯一，同 type 下 dict_value 不能重复；
 *         冲突时捕获 {@code DuplicateKeyException} 转 {@code DICT_DUPLICATE}</li>
 *     <li>更新时 dictType / dictValue 不允许修改，只允许改 dictLabel / sort / status / remark</li>
 *     <li>listByType：type 为 null/空字符串 时返回空列表，不查库</li>
 * </ul>
 */
public interface SysDictService {

    /**
     * 按字典类型查询列表 —— 前端下拉用。
     * <p>
     * type 为 null 或空白时直接返回空列表，不调数据库。
     *
     * @param dictType 字典类型
     * @return 该类型下的所有条目，按 sort 升序
     */
    List<SysDict> listByType(String dictType);

    /**
     * 查询所有已启用的字典条目。
     *
     * @return status=1 的全部条目，按 sort 升序
     */
    List<SysDict> listAllEnabled();

    /**
     * 分页查询（管理页面用）。
     *
     * @param dictType  字典类型（可空，表示不过滤）
     * @param pageNum   页码
     * @param pageSize  每页条数
     * @return 分页结果
     */
    IPage<SysDict> page(String dictType, long pageNum, long pageSize);

    /**
     * 按 id 查询详情。
     *
     * @param id 主键
     * @return 字典实体
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 DICT_NOT_FOUND
     */
    SysDict getById(Long id);

    /**
     * 创建字典条目。
     *
     * @param dto 创建参数
     * @return 新记录 id
     * @throws com.ticket.common.exception.BusinessException dict_value 重复时抛 DICT_DUPLICATE
     */
    Long create(SysDictSaveDTO dto);

    /**
     * 更新字典条目（dictType / dictValue 不可修改）。
     *
     * @param dto 更新参数（含 id）
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 DICT_NOT_FOUND；
     *                                                       dict_value 重复时抛 DICT_DUPLICATE
     */
    void update(SysDictSaveDTO dto);

    /**
     * 删除字典条目。
     *
     * @param id 主键
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 DICT_NOT_FOUND
     */
    void delete(Long id);
}
