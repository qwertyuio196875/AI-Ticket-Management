package com.ticket.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.vo.TicketExportRow;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * {@code ticket_info} 表的 Mapper（ticket 05 + ticket 10 导出）。
 * <p>
 * 标准 CRUD 走 MP {@link BaseMapper} 自带方法；
 * 复杂查询（带 JOIN / 聚合）由 XML 或注解 {@code @Select} 在 Service 层组合。
 * <p>
 * ticket 10 新增 {@link #listExportRows(TicketQueryDTO, LocalDateTime, LocalDateTime)}：
 * 按筛选条件查导出行（JOIN {@code sys_user} 拼创建人 / 处理人昵称）。导出场景不走分页——全量拉取，
 * 用 EasyExcel 流式写出。
 */
public interface TicketInfoMapper extends BaseMapper<TicketInfo> {

    /**
     * 按筛选条件拉取导出行（ticket 10 / ADR-0030）。
     * <p>
     * 返回的 {@link TicketExportRow} 已 JOIN {@code sys_user} 拼装创建人 / 处理人
     * 展示名（creatorName / handlerName），Service 层不需要再二次查询。
     * <p>
     * <b>不去重 soft-delete</b>：{@code ticket_info.is_deleted = 1} 由调用方
     * 在 {@code TicketQueryDTO} 显式过滤；本方法把过滤条件完全交给 SQL 拼装。
     * <p>
     * <b>排序</b>：固定 {@code create_time DESC, id DESC} —— 与列表页默认顺序一致。
     *
     * @param query     筛选条件（status / priority / type / handlerId）
     * @param dateFrom  创建时间下界（含），可空 —— Service 层把 {@code LocalDate} 边界归一化为
     *                 {@code LocalDateTime.of(date, LocalTime.MIN)}
     * @param dateTo    创建时间上界（含），可空 —— Service 层归一化为
     *                 {@code LocalDateTime.of(date, LocalTime.MAX)}，避免 MySQL/H2 把
     *                 {@code LocalDate} 转 DATE 导致"今天"创建工单被漏掉
     * @return 导出行列表（可能为空）
     */
    List<TicketExportRow> listExportRows(@Param("query") TicketQueryDTO query,
                                          @Param("dateFrom") LocalDateTime dateFrom,
                                          @Param("dateTo") LocalDateTime dateTo);
}
