package com.ticket.web.system.vo;

import com.ticket.system.vo.SysMenuTreeVO;

import java.util.List;

/**
 * 通用分页结果 VO（ticket 03 起步开始使用）。
 * <p>
 * 用 record 而非 MyBatis Plus {@code IPage} 直接返回 —— 避免 entity 渗到 Controller 边界。
 */
public record PageVO<T>(
        long total,
        long pageNum,
        long pageSize,
        List<T> records
) {
}