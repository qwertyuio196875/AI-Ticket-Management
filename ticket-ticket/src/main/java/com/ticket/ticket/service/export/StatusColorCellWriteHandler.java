package com.ticket.ticket.service.export;

import com.alibaba.excel.metadata.Head;
import com.alibaba.excel.metadata.data.WriteCellData;
import com.alibaba.excel.write.handler.CellWriteHandler;
import com.alibaba.excel.write.metadata.holder.WriteSheetHolder;
import com.alibaba.excel.write.metadata.holder.WriteTableHolder;
import com.ticket.ticket.enums.TicketStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Excel 导出按状态行着色 Handler（ticket 10 / ADR-0030）。
 * <p>
 * <b>规格</b>（spec AC #3）：
 * <ul>
 *     <li>{@link TicketStatus#PROCESSING} —— 浅蓝背景</li>
 *     <li>{@link TicketStatus#RESOLVED} —— 浅绿背景</li>
 *     <li>{@link TicketStatus#CLOSED} —— 浅灰背景</li>
 *     <li>{@link TicketStatus#PENDING} —— 不着色（默认）</li>
 * </ul>
 *
 * <p><b>实现机制</b>：EasyExcel 写完每个单元格后回调
 * {@link #afterCellDispose} —— 这里拿到当前单元格所在行（{@link Row#getRowNum()}）
 * 以及对应数据行的 status 字段。
 * 由于行内所有单元格共享样式，我们对每个非 header 行 <b>只处理状态列</b>（status 字段所在的列），
 * 然后用 {@link Workbook#createCellStyle()} 缓存样式，对该行其它单元格复用同一 style。
 *
 * <p><b>性能</b>：每行只创建一次样式 + 设置一次 rowHeight（默认已自适应，无需调整）；
 * 用 {@link ConcurrentHashMap} 缓存 (workbook, color) → style 的映射避免重复创建。
 *
 * <p><b>关于 PENDING 不着色的取舍</b>：spec 仅明确 PROCESSING / RESOLVED / CLOSED 三种着色，
 * PENDING 留作默认（白色）。如果未来扩展（比如新加颜色），改 {@link #colorFor} 即可。
 */
public class StatusColorCellWriteHandler implements CellWriteHandler {

    /** 状态字段所在列下标 —— 由 {@code TicketExportRow} 决定 */
    private static final int STATUS_COLUMN_INDEX = 4;

    @Override
    public void afterCellDispose(WriteSheetHolder writeSheetHolder,
                                 WriteTableHolder writeTableHolder,
                                 List<WriteCellData<?>> cellDataList,
                                 Cell cell,
                                 Head head,
                                 Integer relativeRowIndex,
                                 Boolean isHead) {
        // 仅处理数据行（非表头）
        if (Boolean.TRUE.equals(isHead)) {
            return;
        }
        // 仅在该行状态列做样式注入（行级一次即可）
        if (cell.getColumnIndex() != STATUS_COLUMN_INDEX) {
            return;
        }

        // 当前行的 status 字段值就是当前 cell 的字符串值（status 列 → 直接读 cell 即可）
        String statusText = readStatus(cell);
        if (statusText == null) {
            return;
        }
        IndexedColors color = colorFor(statusText);
        if (color == null) {
            return; // PENDING 等不需要着色的状态
        }

        // 找到所在 row，给整行所有 cell 套上样式
        Row row = cell.getRow();
        if (row == null) {
            return;
        }
        Workbook workbook = cell.getSheet().getWorkbook();
        CellStyle style = styleFor(workbook, color);

        // 给该行所有 cell 套样式：覆盖到状态列及之前列（后续列由 EasyExcel 默认回调处理），
        // 这里用 lastCellNum + firstCellNum 安全遍历所有存在 / 不存在的列。
        short firstCol = row.getFirstCellNum();
        short lastCol = row.getLastCellNum();
        if (firstCol < 0) {
            return;
        }
        for (int i = firstCol; i <= lastCol; i++) {
            Cell c = row.getCell(i);
            if (c != null) {
                c.setCellStyle(style);
            }
        }
    }

    /**
     * 把单元格 status 列字符串解析为 {@link TicketStatus}；非法值不参与着色（白底）。
     */
    private static String readStatus(Cell cell) {
        try {
            switch (cell.getCellType()) {
                case STRING:
                    return cell.getStringCellValue();
                default:
                    return null;
            }
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * 把 {@link TicketStatus} 名映射为背景色；PENDING 返回 null（不着色）。
     */
    private static IndexedColors colorFor(String statusName) {
        if (statusName == null) {
            return null;
        }
        try {
            TicketStatus status = TicketStatus.valueOf(statusName);
            switch (status) {
                case PROCESSING:
                    return IndexedColors.LIGHT_CORNFLOWER_BLUE;
                case RESOLVED:
                    return IndexedColors.SEA_GREEN;
                case CLOSED:
                    return IndexedColors.GREY_25_PERCENT;
                default:
                    return null; // PENDING 默认白色
            }
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** workbook 维度缓存样式，避免每次都 createCellStyle */
    private static final Map<String, CellStyle> STYLE_CACHE = new ConcurrentHashMap<>();

    private static CellStyle styleFor(Workbook workbook, IndexedColors color) {
        String key = System.identityHashCode(workbook) + ":" + color.index;
        CellStyle cached = STYLE_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(color.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        STYLE_CACHE.put(key, style);
        return style;
    }
}