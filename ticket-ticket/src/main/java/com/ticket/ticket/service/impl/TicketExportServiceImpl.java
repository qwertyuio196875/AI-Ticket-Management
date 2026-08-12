package com.ticket.ticket.service.impl;

import com.alibaba.excel.EasyExcel;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.service.TicketExportService;
import com.ticket.ticket.service.export.StatusColorCellWriteHandler;
import com.ticket.ticket.vo.TicketExportRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

/**
 * {@link TicketExportService} 实现（ticket 10 / ADR-0030）。
 * <p>
 * <b>职责</b>：
 * <ol>
 *     <li>按筛选条件从 mapper 拉取导出行（JOIN {@code sys_user} 拼装展示名）</li>
 *     <li>用 Apache EasyExcel 流式写入 {@link OutputStream}</li>
 *     <li>注册 {@link StatusColorCellWriteHandler} 实现按状态行着色</li>
 * </ol>
 *
 * <p><b>设计要点</b>：
 * <ul>
 *     <li>数据组装完全由 mapper 的 SQL 完成，Service 不做字段映射</li>
 *     <li><b>dateTo 边界归一化</b>：{@link TicketQueryDTO} 的 {@code dateTo} 表示"含当天的整天"，
 *         这里复制为 {@code LocalDateTime.of(dateTo, LocalTime.MAX)}，让 SQL
 *         {@code create_time <= dateTo} 命中当天 23:59:59。
 *         直接透传 {@code LocalDate} 会让 MySQL/H2 把它当成 00:00:00，导致
 *         "今天创建的工单" 被漏掉（code-review Standards #1）。</li>
 *     <li>空结果集也写 header（spec AC #4 —— 空集合不返回 404）</li>
 *     <li>不关闭流（Spring MVC 生命周期：response 由框架关闭）</li>
 *     <li>写流失败 → 抛 {@code INTERNAL_ERROR}，由 {@code GlobalExceptionHandler} 包装</li>
 * </ul>
 *
 * <p><b>不在本 Service 范围</b>：
 * <ul>
 *     <li>HTTP 响应头设置（{@code Content-Type} / {@code Content-Disposition}）—— Controller 负责</li>
 *     <li>权限校验 —— Controller 层 {@code @PreAuthorize} 把关</li>
 *     <li>AI 分类 / 状态机 —— 导出数据是历史快照，不重新触发业务逻辑</li>
 * </ul>
 */
@Service
public class TicketExportServiceImpl implements TicketExportService {

    private static final Logger log = LoggerFactory.getLogger(TicketExportServiceImpl.class);

    /** 导出 sheet 名 —— 中文 + 简短，避免 Excel 截断 */
    private static final String SHEET_NAME = "工单列表";

    private final TicketInfoMapper ticketInfoMapper;

    public TicketExportServiceImpl(TicketInfoMapper ticketInfoMapper) {
        this.ticketInfoMapper = ticketInfoMapper;
    }

    @Override
    public List<TicketExportRow> listRows(TicketQueryDTO query) {
        validateQuery(query);
        LocalDateTime[] range = normalizeDateRange(query);
        List<TicketExportRow> rows = ticketInfoMapper.listExportRows(query, range[0], range[1]);
        // mapper 契约：返回可能为 null 的实现兼容（如动态代理 stub）—— 兜底空列表
        return rows != null ? rows : Collections.emptyList();
    }

    @Override
    public void export(TicketQueryDTO query, OutputStream out) {
        validateQuery(query);
        if (out == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "输出流不能为空");
        }

        List<TicketExportRow> rows = listRows(query);
        log.info("导出工单: 命中 {} 行, query={}", rows.size(), query);

        // EasyExcel 流式写：底层走 SXSSF 内存友好；空集合也会写出 header + 空 sheet
        EasyExcel.write(out, TicketExportRow.class)
                .registerWriteHandler(new StatusColorCellWriteHandler())
                .head(TicketExportRow.class)
                .sheet(SHEET_NAME)
                .doWrite(rows);
    }

    private static void validateQuery(TicketQueryDTO query) {
        if (query == null) {
            throw BusinessException.of(BusinessExceptionCode.PARAM_INVALID, "查询参数不能为空");
        }
    }

    /**
     * 把 {@link TicketQueryDTO} 的 {@code dateFrom} / {@code dateTo}（{@code LocalDate}）
     * 归一化为 {@code LocalDateTime} 边界，让 mapper SQL 比较 {@code create_time} 时不会
     * 把 LocalDate 隐式转 DATE 导致"今天"创建工单被漏掉。
     *
     * @return {@code [dateFrom, dateTo]} 二元组（任一可能为 null）
     */
    private static LocalDateTime[] normalizeDateRange(TicketQueryDTO query) {
        LocalDate dateFrom = query.getDateFrom();
        LocalDate dateTo = query.getDateTo();
        LocalDateTime fromDt = dateFrom == null ? null : LocalDateTime.of(dateFrom, LocalTime.MIN);
        LocalDateTime toDt = dateTo == null ? null : LocalDateTime.of(dateTo, LocalTime.MAX);
        return new LocalDateTime[]{fromDt, toDt};
    }
}