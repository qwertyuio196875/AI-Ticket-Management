package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.ticket.entity.TicketAttachment;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketAttachmentMapper;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.oss.OssService;
import com.ticket.ticket.vo.TicketAttachmentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketAttachmentServiceImpl} 单元测试（ticket 12 AC）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code upload}：工单不存在 / CLOSED 拒绝；成功落库字段正确 + VO 带 downloadUrl；
 *         OSS 失败原样上抛且不落库</li>
 *     <li>{@code list}：VO 带 downloadUrl；单个签名失败该项为 null 但列表仍返回</li>
 *     <li>{@code delete}：上传者 / 管理员可删（先删对象后软删元数据）；非上传者非管理员拒绝；
 *         附件不存在 / 跨工单拒绝；OSS 删除失败上抛且不软删</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceImplTest {

    @Mock
    TicketInfoMapper ticketInfoMapper;
    @Mock
    TicketAttachmentMapper attachmentMapper;
    @Mock
    OssService ossService;

    @InjectMocks
    TicketAttachmentServiceImpl service;

    private static final Long TICKET_ID = 100L;
    private static final Long OPERATOR_ID = 1L;
    private static final Long ATTACHMENT_ID = 500L;

    private TicketInfo baseTicket;
    private MockedStatic<SecurityContextUtils> securityMock;

    @BeforeEach
    void setUp() {
        baseTicket = new TicketInfo();
        baseTicket.setId(TICKET_ID);
        baseTicket.setTicketNo("TK2026081100000001");
        baseTicket.setTitle("测试工单");
        baseTicket.setStatus(TicketStatus.PENDING);
        baseTicket.setCreatorId(OPERATOR_ID);
        baseTicket.setIsDeleted(TicketInfo.NOT_DELETED);
        // 默认无 admin 权限
        securityMock = Mockito.mockStatic(SecurityContextUtils.class, Mockito.CALLS_REAL_METHODS);
        securityMock.when(() -> SecurityContextUtils.hasAuthority("admin")).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityMock.close();
        SecurityContextHolder.clearContext();
    }

    // ==================== upload ====================

    @Test
    @DisplayName("upload 工单不存在：抛 TICKET_NOT_FOUND(T0101)，不调 ossService")
    void upload_ticket_not_found_throws_T0101() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.upload(TICKET_ID, new MockMultipartFile("f", new byte[0]), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
        verify(ossService, never()).upload(any());
        verify(attachmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("upload 工单已关闭：抛 TICKET_CLOSED(T0103)，不调 ossService")
    void upload_closed_ticket_throws_T0103() {
        baseTicket.setStatus(TicketStatus.CLOSED);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        assertThatThrownBy(() -> service.upload(TICKET_ID, new MockMultipartFile("f", new byte[0]), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.TICKET_CLOSED.getCode());
        verify(ossService, never()).upload(any());
    }

    @Test
    @DisplayName("upload 成功：ossService.upload 得到 key → 落库字段正确 → VO 带 downloadUrl")
    void upload_success_persists_metadata() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        when(ossService.upload(any())).thenReturn("ticket/20260813/abc.png");
        when(ossService.getSignedUrl("ticket/20260813/abc.png")).thenReturn("https://signed-url/abc.png");
        Mockito.doAnswer(inv -> {
            TicketAttachment a = inv.getArgument(0);
            a.setId(ATTACHMENT_ID);
            return 1;
        }).when(attachmentMapper).insert(any(TicketAttachment.class));

        MockMultipartFile file = new MockMultipartFile("file", "截图.png", "image/png", new byte[1024]);
        TicketAttachmentVO vo = service.upload(TICKET_ID, file, OPERATOR_ID);

        assertThat(vo.getId()).isEqualTo(ATTACHMENT_ID);
        assertThat(vo.getDownloadUrl()).isEqualTo("https://signed-url/abc.png");

        ArgumentCaptor<TicketAttachment> captor = ArgumentCaptor.forClass(TicketAttachment.class);
        verify(attachmentMapper, times(1)).insert(captor.capture());
        TicketAttachment written = captor.getValue();
        assertThat(written.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(written.getFileUrl()).isEqualTo("ticket/20260813/abc.png");
        assertThat(written.getFileName()).isEqualTo("截图.png");
        assertThat(written.getSize()).isEqualTo(1024L);
        assertThat(written.getMimeType()).isEqualTo("image/png");
        assertThat(written.getUploaderId()).isEqualTo(OPERATOR_ID);
        assertThat(written.getUploadTime()).isNotNull();
        assertThat(written.getIsDeleted()).isEqualTo(TicketAttachment.NOT_DELETED);
    }

    @Test
    @DisplayName("upload ossService 抛 BusinessException：原样上抛（A0201），insert 从未调用")
    void upload_oss_failure_does_not_persist() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        when(ossService.upload(any()))
                .thenThrow(BusinessException.of(BusinessExceptionCode.OSS_UPLOAD_FAILED));

        assertThatThrownBy(() -> service.upload(TICKET_ID, new MockMultipartFile("f", new byte[0]), OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.OSS_UPLOAD_FAILED.getCode());
        verify(attachmentMapper, never()).insert(any());
    }

    // ==================== list ====================

    @Test
    @DisplayName("list：返回 VO 列表且每个带 downloadUrl")
    void list_returns_vos_with_download_url() {
        when(attachmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newAttachment(ATTACHMENT_ID, "ticket/a.png"),
                        newAttachment(ATTACHMENT_ID + 1, "ticket/b.pdf")));
        when(ossService.getSignedUrl("ticket/a.png")).thenReturn("https://s/a.png");
        when(ossService.getSignedUrl("ticket/b.pdf")).thenReturn("https://s/b.pdf");

        List<TicketAttachmentVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDownloadUrl()).isEqualTo("https://s/a.png");
        assertThat(result.get(1).getDownloadUrl()).isEqualTo("https://s/b.pdf");
        assertThat(result.get(0).getTicketId()).isEqualTo(TICKET_ID);
    }

    @Test
    @DisplayName("list：单个 getSignedUrl 抛异常 → 该项 downloadUrl 为 null，列表仍返回")
    void list_tolerates_signed_url_failure() {
        when(attachmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(newAttachment(ATTACHMENT_ID, "ticket/a.png"),
                        newAttachment(ATTACHMENT_ID + 1, "ticket/b.pdf")));
        when(ossService.getSignedUrl("ticket/a.png")).thenReturn("https://s/a.png");
        when(ossService.getSignedUrl("ticket/b.pdf")).thenThrow(new RuntimeException("boom"));

        List<TicketAttachmentVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getDownloadUrl()).isEqualTo("https://s/a.png");
        assertThat(result.get(1).getDownloadUrl()).isNull();
    }

    @Test
    @DisplayName("list 工单无附件：返回空列表，不调 ossService")
    void list_empty_returns_empty() {
        when(attachmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TicketAttachmentVO> result = service.list(TICKET_ID);

        assertThat(result).isEmpty();
        verify(ossService, never()).getSignedUrl(anyString());
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete 上传者本人：先 oss.delete 删对象，后 deleteById 软删元数据")
    void delete_by_uploader_succeeds() {
        TicketAttachment existing = newAttachment(ATTACHMENT_ID, "ticket/a.png");
        existing.setUploaderId(OPERATOR_ID);
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(existing);
        when(attachmentMapper.deleteById(ATTACHMENT_ID)).thenReturn(1);

        service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID);

        InOrder inOrder = inOrder(ossService, attachmentMapper);
        inOrder.verify(ossService).delete("ticket/a.png");
        inOrder.verify(attachmentMapper).deleteById(ATTACHMENT_ID);
    }

    @Test
    @DisplayName("delete 管理员：可删他人附件")
    void delete_by_admin_succeeds() {
        securityMock.when(() -> SecurityContextUtils.hasAuthority("admin")).thenReturn(true);
        TicketAttachment existing = newAttachment(ATTACHMENT_ID, "ticket/a.png");
        existing.setUploaderId(99L);
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(existing);
        when(attachmentMapper.deleteById(ATTACHMENT_ID)).thenReturn(1);

        service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID);

        verify(ossService).delete("ticket/a.png");
        verify(attachmentMapper, times(1)).deleteById(ATTACHMENT_ID);
    }

    @Test
    @DisplayName("delete 非上传者非管理员：抛 AUTH_FORBIDDEN(403)，不删对象不软删元数据")
    void delete_by_other_non_admin_throws_FORBIDDEN() {
        TicketAttachment existing = newAttachment(ATTACHMENT_ID, "ticket/a.png");
        existing.setUploaderId(99L);
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.AUTH_FORBIDDEN.getCode());
        verify(ossService, never()).delete(anyString());
        verify(attachmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete 附件不存在：抛 ATTACHMENT_NOT_FOUND(T0106)")
    void delete_nonexistent_attachment_throws_T0106() {
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.ATTACHMENT_NOT_FOUND.getCode());
        verify(ossService, never()).delete(anyString());
    }

    @Test
    @DisplayName("delete 附件跨工单：抛 ATTACHMENT_NOT_FOUND")
    void delete_attachment_belongs_to_other_ticket_throws_T0106() {
        TicketAttachment existing = newAttachment(ATTACHMENT_ID, "ticket/a.png");
        existing.setTicketId(999L);
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.ATTACHMENT_NOT_FOUND.getCode());
        verify(ossService, never()).delete(anyString());
    }

    @Test
    @DisplayName("delete ossService.delete 抛异常：A0202 原样上抛，deleteById 未调用")
    void delete_oss_failure_does_not_delete_metadata() {
        TicketAttachment existing = newAttachment(ATTACHMENT_ID, "ticket/a.png");
        existing.setUploaderId(OPERATOR_ID);
        when(attachmentMapper.selectById(ATTACHMENT_ID)).thenReturn(existing);
        doThrow(BusinessException.of(BusinessExceptionCode.OSS_DELETE_FAILED))
                .when(ossService).delete("ticket/a.png");

        assertThatThrownBy(() -> service.delete(TICKET_ID, ATTACHMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.OSS_DELETE_FAILED.getCode());
        verify(attachmentMapper, never()).deleteById(anyLong());
    }

    // ---------- 辅助 ----------

    private TicketAttachment newAttachment(Long id, String fileUrl) {
        TicketAttachment a = new TicketAttachment();
        a.setId(id);
        a.setTicketId(TICKET_ID);
        a.setFileUrl(fileUrl);
        a.setFileName("name.png");
        a.setSize(100L);
        a.setMimeType("image/png");
        a.setUploaderId(OPERATOR_ID);
        a.setUploadTime(LocalDateTime.of(2026, 8, 12, 10, 0));
        a.setIsDeleted(TicketAttachment.NOT_DELETED);
        return a;
    }
}
