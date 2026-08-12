package com.ticket.ticket.service.export;

import com.ticket.ticket.enums.TicketStatus;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link StatusColorCellWriteHandler} 单元测试（ticket 10 AC）。
 * <p>
 * 通过 Mockito + POI mock 验证 handler 的关键决策点：
 * <ul>
 *     <li>仅处理数据行（{@code isHead=true} 时跳过）</li>
 *     <li>仅处理状态列（避免每 cell 都重建样式）</li>
 *     <li>PENDING 不参与着色</li>
 *     <li>PROCESSING → 浅蓝，RESOLVED → 浅绿，CLOSED → 浅灰</li>
 *     <li>行内所有 cell 都套上同一样式（视觉对齐）</li>
 * </ul>
 *
 * <p><b>POI 行为说明</b>：handler 接受的是 POI {@link Cell}，这里用 Mockito 模拟；
 * style 创建走真实 {@link SXSSFWorkbook}（最小代价真实 workbook）以验证
 * {@link FillPatternType} 和 {@link IndexedColors} 的填充语义。
 */
class StatusColorCellWriteHandlerTest {

    private final StatusColorCellWriteHandler handler = new StatusColorCellWriteHandler();

    @Test
    @DisplayName("isHead=true 时直接返回，不读 cell 也不创建样式")
    void skipHeadRow_doesNotTouchCell() {
        Cell cell = mock(Cell.class);

        handler.afterCellDispose(null, null, null, cell, null, 0, Boolean.TRUE);

        // isHead=true 应直接 return，不调用任何 cell 方法
        verify(cell, never()).getCellType();
        verify(cell, never()).getRow();
    }

    @Test
    @DisplayName("非状态列：直接返回（避免每 cell 都重建样式）")
    void skipNonStatusColumn_doesNotTouchCell() {
        Cell cell = mock(Cell.class);
        when(cell.getColumnIndex()).thenReturn(0); // 第一列（工单号）

        handler.afterCellDispose(null, null, null, cell, null, 0, Boolean.FALSE);

        // 非状态列应直接 return
        verify(cell, never()).getCellType();
        verify(cell, never()).getStringCellValue();
    }

    @Test
    @DisplayName("PENDING 状态：不创建样式（白底）")
    void pendingStatus_doesNotApplyStyle() {
        Cell cell = mockPendingCell();

        handler.afterCellDispose(null, null, null, cell, null, 0, Boolean.FALSE);

        // 不读 row / sheet / workbook → 不创建样式
        verify(cell, never()).getRow();
    }

    @Test
    @DisplayName("PROCESSING 状态：创建浅蓝样式（背景填充 SOLID_FOREGROUND）")
    void processingStatus_appliesLightBlueStyle() {
        applyStatusAndVerifyFill(TicketStatus.PROCESSING, IndexedColors.LIGHT_CORNFLOWER_BLUE);
    }

    @Test
    @DisplayName("RESOLVED 状态：创建浅绿样式")
    void resolvedStatus_appliesSeaGreenStyle() {
        applyStatusAndVerifyFill(TicketStatus.RESOLVED, IndexedColors.SEA_GREEN);
    }

    @Test
    @DisplayName("CLOSED 状态：创建浅灰样式")
    void closedStatus_appliesGreyStyle() {
        applyStatusAndVerifyFill(TicketStatus.CLOSED, IndexedColors.GREY_25_PERCENT);
    }

    @Test
    @DisplayName("行内所有 cell 都被套上同一样式")
    void entireRow_cellsShareSameStyle() {
        try (Workbook wb = new SXSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell c0 = row.createCell(0);
            c0.setCellValue("TK001");
            Cell c1 = row.createCell(1);
            c1.setCellValue("标题");
            Cell c4 = row.createCell(4);
            c4.setCellValue("PROCESSING");
            Cell c7 = row.createCell(7);
            c7.setCellValue("2026-08-12");

            // handler 直接调 —— c4 是状态列 → 触发整行染色
            handler.afterCellDispose(null, null, null, c4, null, 0, Boolean.FALSE);

            // 0,1,4,7 列的 cell 都应被设置样式
            assertThat(c0.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            assertThat(c1.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            assertThat(c4.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            assertThat(c7.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            // 都是 SOLID_FOREGROUND
            assertThat(c0.getCellStyle().getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    @DisplayName("非法状态名：handler 不抛错，跳过（兼容脏数据）")
    void invalidStatusName_doesNotThrow() {
        Cell cell = mock(Cell.class);
        when(cell.getColumnIndex()).thenReturn(StatusColorCellWriteHandlerTest.STATUS_COL);
        when(cell.getCellType()).thenReturn(org.apache.poi.ss.usermodel.CellType.STRING);
        when(cell.getStringCellValue()).thenReturn("UNKNOWN_STATUS");

        // 不抛错
        handler.afterCellDispose(null, null, null, cell, null, 0, Boolean.FALSE);

        verify(cell, times(1)).getStringCellValue();
        verify(cell, never()).getRow();
    }

    // ---------- 辅助 ----------

    private static final int STATUS_COL = 4;

    private static Cell mockPendingCell() {
        Cell cell = mock(Cell.class);
        when(cell.getColumnIndex()).thenReturn(STATUS_COL);
        when(cell.getCellType()).thenReturn(org.apache.poi.ss.usermodel.CellType.STRING);
        when(cell.getStringCellValue()).thenReturn(TicketStatus.PENDING.name());
        return cell;
    }

    /**
     * 通用 helper：构造一个真实的 Sheet + Row + 状态列 Cell + 验证样式填充。
     */
    private void applyStatusAndVerifyFill(TicketStatus status, IndexedColors expected) {
        try (Workbook wb = new SXSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row row = sheet.createRow(0);
            Cell statusCell = row.createCell(STATUS_COL);
            statusCell.setCellValue(status.name());
            Cell otherCell = row.createCell(0);
            otherCell.setCellValue("TK001");

            handler.afterCellDispose(null, null, null, statusCell, null, 0, Boolean.FALSE);

            CellStyle applied = statusCell.getCellStyle();
            assertThat(applied).isNotNull();
            assertThat(applied.getFillForegroundColor()).isEqualTo(expected.getIndex());
            assertThat(applied.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);

            // 同行其他 cell 也被覆盖
            assertThat(otherCell.getCellStyle().getFillForegroundColor()).isEqualTo(expected.getIndex());
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }
}