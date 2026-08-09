package com.ticket.system.service;

import com.ticket.system.dto.TicketCategorySaveDTO;
import com.ticket.system.entity.TicketCategory;

import java.util.List;

/**
 * 工单分类服务接口（ticket 04）。
 * <p>
 * 业务规则：
 * <ul>
 *     <li>name 唯一索引，冲突时捕获 {@code DuplicateKeyException} 转 {@code CATEGORY_DUPLICATE}</li>
 *     <li>listAllEnabled：只返回已启用的分类，供前端下拉用</li>
 * </ul>
 */
public interface TicketCategoryService {

    /**
     * 查询所有已启用的工单分类。
     *
     * @return status=1 的全部条目，按 sort 升序
     */
    List<TicketCategory> listAllEnabled();

    /**
     * 按 id 查询详情。
     *
     * @param id 主键
     * @return 分类实体
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 CATEGORY_NOT_FOUND
     */
    TicketCategory getById(Long id);

    /**
     * 创建工单分类。
     *
     * @param dto 创建参数
     * @return 新记录 id
     * @throws com.ticket.common.exception.BusinessException name 重复时抛 CATEGORY_DUPLICATE
     */
    Long create(TicketCategorySaveDTO dto);

    /**
     * 更新工单分类。
     *
     * @param dto 更新参数（含 id）
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 CATEGORY_NOT_FOUND；
     *                                                       name 重复时抛 CATEGORY_DUPLICATE
     */
    void update(TicketCategorySaveDTO dto);

    /**
     * 删除工单分类。
     *
     * @param id 主键
     * @throws com.ticket.common.exception.BusinessException id 不存在时抛 CATEGORY_NOT_FOUND
     */
    void delete(Long id);
}
