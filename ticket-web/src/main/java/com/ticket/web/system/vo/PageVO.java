package com.ticket.web.system.vo;

import com.ticket.system.vo.SysMenuTreeVO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 通用分页结果 VO（ticket 03 起步开始使用）。
 * <p>
 * 用 record 而非 MyBatis Plus {@code IPage} 直接返回 —— 避免 entity 渗到 Controller 边界。
 */
@Schema(description = "通用分页结果")
public record PageVO<T>(
        @Schema(description = "总条数", example = "100")
        long total,
        @Schema(description = "当前页码", example = "1")
        long pageNum,
        @Schema(description = "每页条数", example = "20")
        long pageSize,
        @Schema(description = "当前页数据")
        List<T> records
) {
}