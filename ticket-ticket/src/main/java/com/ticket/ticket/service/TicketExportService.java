package com.ticket.ticket.service;

import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.vo.TicketExportRow;

import java.io.OutputStream;
import java.util.List;

/**
 * 工单列表 Excel 导出服务（ticket 10 / ADR-0030）。
 * <p>
 * 端到端：按 {@link TicketQueryDTO} 过滤 → 拉取 {@link TicketExportRow} → 用
 * Apache EasyExcel 流式写出到 {@link OutputStream}（HTTP response / 本地文件均可）。
 * <p>
 * 该接口存在的意义：
 * <ul>
 *     <li>把"导出样式 + 数据拼装"集中到一个 Service，避免 Controller 拼装行</li>
 *     <li>为单测提供 seam —— mock mapper 看数据如何组装到 Excel</li>
 *     <li>导出列 / 颜色规则独立于 {@code TicketVO}，避免影响普通列表返回</li>
 * </ul>
 */
public interface TicketExportService {

    /**
     * 按筛选条件导出 .xlsx 到输出流。
     * <p>
     * 流由调用方持有（{@code HttpServletResponse.getOutputStream()} 或临时文件）；
     * 本方法<b>不会关闭</b>流，由调用方负责（与 Spring MVC 生命周期一致）。
     * <p>
     * 空结果集也会写一个仅含表头的合法 .xlsx（spec AC #4）。
     *
     * @param query 筛选条件（与列表端点同语义）
     * @param out   写出流（不会关闭）
     * @throws com.ticket.common.exception.BusinessException 入参非法
     */
    void export(TicketQueryDTO query, OutputStream out);

    /**
     * 当前筛选条件下的导出总行数（用于日志 / 监控）。
     * <p>
     * <b>注意</b>：该方法不直接走 mapper.count，而是用 {@link #listRows(TicketQueryDTO)}
     * 拿到结果集的 {@code size()}——避免分页限制（export 全量导出）。
     *
     * @param query 筛选条件
     * @return 总行数
     */
    default int countForExport(TicketQueryDTO query) {
        return listRows(query).size();
    }

    /**
     * 暴露给单测 / 内部调用的纯函数式方法：把 query 转换成导出行列表。
     * <p>
     * 拆出来让 {@link #export(TicketQueryDTO, OutputStream)} 实现可单测且纯转写，
     * 也便于单测不依赖 EasyExcel IO 直接断言行数 / 字段映射。
     */
    List<TicketExportRow> listRows(TicketQueryDTO query);
}