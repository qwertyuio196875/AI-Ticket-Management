package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.entity.TicketComment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单评论 Mapper（ticket 07）。
 * <p>
 * 继承 MyBatis Plus {@link BaseMapper} 即可满足 ticket 07 范围内的全部
 * CRUD 需求：
 * <ul>
 *     <li>{@code selectList(LambdaQueryWrapper)} —— 列表查询
 *         （{@code WHERE ticket_id = ? AND is_deleted = 0}），
 *         排序由调用方通过 wrapper.orderByAsc 拼装</li>
 *     <li>{@code insert(TicketComment)} —— 新增</li>
 *     <li>{@code selectById(Long)} —— 单条查询（带软删过滤）</li>
 *     <li>{@code deleteById(Long)} —— 软删（MP {@code @TableLogic}）</li>
 * </ul>
 * <p>
 * <b>当前不引入自定义 SQL</b>：嵌套回复、INTERNAL 过滤都在 Service 层做。
 */
@Mapper
public interface TicketCommentMapper extends BaseMapper<TicketComment> {
}
