package com.ticket.ticket.vo;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.format.DateTimeFormat;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 工单列表 Excel 导出行（ticket 10 / ADR-0030）。
 * <p>
 * 字段映射通过 {@link ExcelProperty} 直接控制 Excel 表头文案，避免在 Service 层做映射
 * （EasyExcel 反射写入）。
 * <p>
 * <b>列宽</b>：用 {@link ColumnWidth} 标注每列宽度（字符数）；EasyExcel 按字符数 × 256
 * 算像素。不写宽度时默认 20。
 *
 * <p><b>类型选择</b>：
 * <ul>
 *     <li>{@code creatorName} / {@code handlerName} —— 用 {@link String}，EasyExcel 直接写出；
 *         空值显示为空单元格（不抛 NPE）</li>
 *     <li>{@code createTime} —— {@link LocalDateTime} + {@link DateTimeFormat}
 *         按 {@code "yyyy-MM-dd HH:mm:ss"} 写出；与系统其他模块日志格式保持一致</li>
 *     <li>其余字段与 {@link TicketVO} 同类型，保证数据传递链路一致</li>
 * </ul>
 *
 * <p><b>状态着色</b>：本类不带样式注解，由 {@code StatusColorCellWriteHandler}
 * 按行 status 字段动态着色（spec / ticket 10 AC）。
 */
@Data
public class TicketExportRow {

    @ExcelProperty(value = "工单号", index = 0)
    @ColumnWidth(22)
    private String ticketNo;

    @ExcelProperty(value = "标题", index = 1)
    @ColumnWidth(30)
    private String title;

    @ExcelProperty(value = "类型", index = 2)
    @ColumnWidth(14)
    private String type;

    @ExcelProperty(value = "优先级", index = 3)
    @ColumnWidth(10)
    private String priority;

    @ExcelProperty(value = "状态", index = 4)
    @ColumnWidth(12)
    private String status;

    @ExcelProperty(value = "创建人", index = 5)
    @ColumnWidth(14)
    private String creatorName;

    @ExcelProperty(value = "处理人", index = 6)
    @ColumnWidth(14)
    private String handlerName;

    @ExcelProperty(value = "创建时间", index = 7)
    @ColumnWidth(20)
    @DateTimeFormat("yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}