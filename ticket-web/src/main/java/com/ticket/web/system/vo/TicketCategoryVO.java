package com.ticket.web.system.vo;

import com.ticket.system.entity.TicketCategory;

import java.time.LocalDateTime;

/**
 * 工单分类 VO —— 前端展示用。
 */
public record TicketCategoryVO(
        Long id,
        String name,
        String description,
        Integer sort,
        Integer status,
        LocalDateTime createTime
) {

    public static TicketCategoryVO from(TicketCategory category) {
        return new TicketCategoryVO(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getSort(),
                category.getStatus(),
                category.getCreateTime());
    }
}
