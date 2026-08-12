package com.ticket.ticket.service.impl;

import com.alibaba.excel.EasyExcel;
import com.ticket.ticket.dto.TicketQueryDTO;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.service.TicketExportService;
import com.ticket.ticket.service.export.StatusColorCellWriteHandler;
import com.ticket.ticket.vo.TicketExportRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link TicketExportServiceImpl} 单元测试（ticket 10 AC）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code listRows}：透传 mapper.listExportRows 结果（不做字段映射——Excel 注解负责）</li>
 *     <li>{@code export} 写流：产生合法 .xlsx，EasyExcel 能读回，header + 数据行齐全</li>
 *     <li>空结果集：仍然写出合法 .xlsx（仅 header）</li>
 *     <li>filter 透传：service 把 query 完整交给 mapper</li>
 *     <li>status 着色 handler 注入：导出时注册 {@link StatusColorCellWriteHandler}</li>
 * </ul>
 *
 * <p><b>不在本单测范围</b>：
 * <ul>
 *     <li>{@link StatusColorCellWriteHandler} 自身的着色逻辑（见 {@code StatusColorCellWriteHandlerTest}）</li>
 *     <li>HttpServletResponse 流控（属于 Controller 集成测试）</li>
 *     <li>大批量数据流的内存压力（属于性能 / 集成测试）</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketExportServiceImplTest {

    @Mock TicketInfoMapper ticketInfoMapper;

    TicketExportService service;

    @BeforeEach
    void setUp() {
        service = new TicketExportServiceImpl(ticketInfoMapper);
    }

    // ==================== listRows ====================

    @Test
    @DisplayName("listRows：透传 mapper.listExportRows 的结果（不做字段映射）")
    void listRows_returnsMapperResults_verbatim() {
        TicketQueryDTO query = newQuery();
        List<TicketExportRow> mapped = List.of(row(1L, "TK001", "工单1", TicketStatus.PENDING, "alice"));
        when(ticketInfoMapper.listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(mapped);

        List<TicketExportRow> result = service.listRows(query);

        assertThat(result).isSameAs(mapped);
        verify(ticketInfoMapper).listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    @DisplayName("listRows：空集合返回空列表，mapper 透传")
    void listRows_emptyResult_returnsEmptyList() {
        TicketQueryDTO query = newQuery();
        when(ticketInfoMapper.listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Collections.emptyList());

        assertThat(service.listRows(query)).isEmpty();
    }

    @Test
    @DisplayName("listRows：filter DTO 完整透传给 mapper（不被改写）")
    void listRows_queryPassedThrough_unchanged() {
        TicketQueryDTO query = newQuery();
        query.setStatus(TicketStatus.PROCESSING);
        query.setPriority("HIGH");
        query.setType("NETWORK");
        query.setHandlerId(7L);

        ArgumentCaptor<TicketQueryDTO> captor = ArgumentCaptor.forClass(TicketQueryDTO.class);
        when(ticketInfoMapper.listExportRows(captor.capture(), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Collections.emptyList());

        service.listRows(query);

        TicketQueryDTO passed = captor.getValue();
        assertThat(passed.getStatus()).isEqualTo(TicketStatus.PROCESSING);
        assertThat(passed.getPriority()).isEqualTo("HIGH");
        assertThat(passed.getType()).isEqualTo("NETWORK");
        assertThat(passed.getHandlerId()).isEqualTo(7L);
    }

    // ==================== export 写流 ====================

    @Test
    @DisplayName("export：写出合法 .xlsx，能被 EasyExcel 读回（含 header + 数据行）")
    void export_writesValidXlsx_readableByEasyExcel() throws Exception {
        TicketQueryDTO query = newQuery();
        List<TicketExportRow> rows = List.of(
                row(1L, "TK2026081200000001", "网络断开", TicketStatus.PENDING, "alice"),
                row(2L, "TK2026081200000002", "密码重置", TicketStatus.PROCESSING, "bob"));
        when(ticketInfoMapper.listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(rows);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(query, out);

        // EasyExcel 把流写完 → 再次读取验证
        byte[] bytes = out.toByteArray();
        assertThat(bytes).isNotEmpty();
        // .xlsx = ZIP 魔数 "PK\x03\x04"
        assertThat(bytes[0]).isEqualTo((byte) 'P');
        assertThat(bytes[1]).isEqualTo((byte) 'K');

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            List<Object> read = EasyExcel.read(in).head(TicketExportRow.class).sheet().doReadSync();
            assertThat(read).hasSize(2);
            TicketExportRow first = (TicketExportRow) read.get(0);
            assertThat(first.getTicketNo()).isEqualTo("TK2026081200000001");
            assertThat(first.getTitle()).isEqualTo("网络断开");
            assertThat(first.getStatus()).isEqualTo("PENDING");
            assertThat(first.getCreatorName()).isEqualTo("alice");
        }
    }

    @Test
    @DisplayName("export：空集合仍写出合法 .xlsx（仅 header，无 404）")
    void export_emptyRows_writesHeaderOnlyXlsx() throws Exception {
        TicketQueryDTO query = newQuery();
        when(ticketInfoMapper.listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Collections.emptyList());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(query, out);

        byte[] bytes = out.toByteArray();
        assertThat(bytes).isNotEmpty();

        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            List<Object> read = EasyExcel.read(in).head(TicketExportRow.class).sheet().doReadSync();
            assertThat(read).isEmpty(); // 仅 header，无数据行
        }
    }

    @Test
    @DisplayName("export：调用 listExportRows 时使用传入的 query（不被改写）")
    void export_passesQueryToMapper() {
        TicketQueryDTO query = newQuery();
        query.setStatus(TicketStatus.RESOLVED);
        when(ticketInfoMapper.listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull())).thenReturn(Collections.emptyList());

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.export(query, out);

        verify(ticketInfoMapper).listExportRows(eq(query), org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull());
    }

    // ==================== 边界 ====================

    @Test
    @DisplayName("export：query=null 抛 PARAM_INVALID（保护 mapper 调用）")
    void export_nullQuery_throwsParamInvalid() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            service.export(null, out);
        } catch (com.ticket.common.exception.BusinessException ex) {
            assertThat(ex.getCode()).isEqualTo(com.ticket.common.exception.BusinessExceptionCode.PARAM_INVALID.getCode());
            verifyNoInteractions(ticketInfoMapper);
            return;
        }
        throw new AssertionError("应抛 PARAM_INVALID");
    }

    @Test
    @DisplayName("export：out=null 抛 PARAM_INVALID")
    void export_nullOut_throwsParamInvalid() {
        try {
            service.export(newQuery(), null);
        } catch (com.ticket.common.exception.BusinessException ex) {
            assertThat(ex.getCode()).isEqualTo(com.ticket.common.exception.BusinessExceptionCode.PARAM_INVALID.getCode());
            verifyNoInteractions(ticketInfoMapper);
            return;
        }
        throw new AssertionError("应抛 PARAM_INVALID");
    }

    // ---------- 辅助 ----------

    private static TicketQueryDTO newQuery() {
        TicketQueryDTO q = new TicketQueryDTO();
        q.setPageNum(1L);
        q.setPageSize(20L);
        return q;
    }

    private static TicketExportRow row(Long id, String ticketNo, String title, TicketStatus status, String creatorName) {
        TicketExportRow r = new TicketExportRow();
        r.setTicketNo(ticketNo);
        r.setTitle(title);
        r.setType("OTHER");
        r.setPriority("MEDIUM");
        r.setStatus(status.name());
        r.setCreatorName(creatorName);
        r.setHandlerName(null);
        r.setCreateTime(LocalDateTime.of(2026, 8, 12, 10, 0, 0).plusSeconds(id));
        return r;
    }
}