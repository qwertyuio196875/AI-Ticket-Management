package com.ticket.ticket.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.security.context.SecurityContextUtils;
import com.ticket.system.entity.SysUser;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.ticket.dto.TicketCommentCreateDTO;
import com.ticket.ticket.entity.TicketComment;
import com.ticket.ticket.entity.TicketInfo;
import com.ticket.ticket.entity.TicketLog;
import com.ticket.ticket.enums.CommentType;
import com.ticket.ticket.enums.TicketEventType;
import com.ticket.ticket.enums.TicketStatus;
import com.ticket.ticket.mapper.TicketCommentMapper;
import com.ticket.ticket.mapper.TicketInfoMapper;
import com.ticket.ticket.mapper.TicketLogMapper;
import com.ticket.ticket.vo.TicketCommentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketCommentServiceImpl} 单元测试（ticket 07 AC）。
 * <p>
 * 纯 JUnit 5 + Mockito，不启动 Spring 上下文，毫秒级运行。
 * <p>
 * <b>覆盖</b>：
 * <ul>
 *     <li>{@code add}：参数校验（ticketId / dto / content 空 / 超长 / 非法 commentType）、
 *         工单不存在 / CLOSED 拒绝、parent 校验（不存在 / 跨工单）、
 *         内容 XSS escape 落库、ticket_log(COMMENTED) 同事务写入</li>
 *     <li>{@code list}：ASC 排序（由 wrapper 控制）、INTERNAL 过滤
 *         （无 ticket:comment 权限时隐藏 / 有 ticket:comment 时可见）、
 *         creatorName 拼装</li>
 *     <li>{@code delete}：仅创建者 / 管理员可删、跨工单评论 / 不存在评论拒绝</li>
 *     <li>XSS 工具：5 个特殊字符的 escape 行为</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketCommentServiceImplTest {

    @Mock TicketCommentMapper ticketCommentMapper;
    @Mock TicketInfoMapper ticketInfoMapper;
    @Mock TicketLogMapper ticketLogMapper;
    @Mock SysUserMapper sysUserMapper;

    @InjectMocks TicketCommentServiceImpl service;

    private static final Long TICKET_ID = 100L;
    private static final Long CREATOR_ID = 1L;
    private static final Long OPERATOR_ID = 1L;
    private static final Long COMMENT_ID = 500L;
    private static final Long PARENT_ID = 499L;

    private TicketInfo baseTicket;
    private MockedStatic<SecurityContextUtils> securityMock;

    @BeforeEach
    void setUp() {
        baseTicket = new TicketInfo();
        baseTicket.setId(TICKET_ID);
        baseTicket.setTicketNo("TK2026081100000001");
        baseTicket.setTitle("测试工单");
        baseTicket.setStatus(TicketStatus.PENDING);
        baseTicket.setCreatorId(CREATOR_ID);
        baseTicket.setIsDeleted(TicketInfo.NOT_DELETED);
        // SecurityContext 默认无 ticket:comment / admin —— 大多数用例不应当看到 INTERNAL
        securityMock = Mockito.mockStatic(SecurityContextUtils.class, Mockito.CALLS_REAL_METHODS);
        securityMock.when(() -> SecurityContextUtils.hasAuthority("ticket:comment")).thenReturn(false);
        securityMock.when(() -> SecurityContextUtils.hasAuthority("admin")).thenReturn(false);
    }

    @AfterEach
    void tearDown() {
        securityMock.close();
        SecurityContextHolder.clearContext();
    }

    // ==================== add ====================

    @Test
    @DisplayName("add 成功：写入 ticket_comment + ticket_log(COMMENTED) 同事务")
    void add_writes_comment_and_log() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        Mockito.doAnswer(invocation -> {
            TicketComment c = invocation.getArgument(0);
            c.setId(COMMENT_ID);
            return 1;
        }).when(ticketCommentMapper).insert(any(TicketComment.class));

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("客户反馈：VPN 登录不上");
        dto.setCommentType("CUSTOMER");

        Long result = service.add(TICKET_ID, dto, OPERATOR_ID);

        assertThat(result).isEqualTo(COMMENT_ID);

        ArgumentCaptor<TicketComment> commentCaptor = ArgumentCaptor.forClass(TicketComment.class);
        verify(ticketCommentMapper, times(1)).insert(commentCaptor.capture());
        TicketComment written = commentCaptor.getValue();
        assertThat(written.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(written.getContent()).isEqualTo("客户反馈：VPN 登录不上");
        assertThat(written.getCommentType()).isEqualTo(CommentType.CUSTOMER);
        assertThat(written.getCreatorId()).isEqualTo(OPERATOR_ID);
        assertThat(written.getParentId()).isNull();
        assertThat(written.getIsDeleted()).isEqualTo(TicketComment.NOT_DELETED);
        assertThat(written.getCreateTime()).isNotNull();

        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper, times(1)).insert(logCaptor.capture());
        TicketLog logWritten = logCaptor.getValue();
        assertThat(logWritten.getTicketId()).isEqualTo(TICKET_ID);
        assertThat(logWritten.getEventType()).isEqualTo(TicketEventType.COMMENTED);
        assertThat(logWritten.getOperatorId()).isEqualTo(OPERATOR_ID);
        assertThat(logWritten.getContent())
                .contains("commentId=" + COMMENT_ID)
                .contains("commentType=CUSTOMER");
        assertThat(logWritten.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("add 嵌套回复：parentId 指向同工单评论 → 写入并记录 parentId")
    void add_nested_reply_with_valid_parent() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        TicketComment parent = new TicketComment();
        parent.setId(PARENT_ID);
        parent.setTicketId(TICKET_ID);
        when(ticketCommentMapper.selectById(PARENT_ID)).thenReturn(parent);
        Mockito.doAnswer(inv -> {
            TicketComment c = inv.getArgument(0);
            c.setId(COMMENT_ID);
            return 1;
        }).when(ticketCommentMapper).insert(any(TicketComment.class));

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("回复上文");
        dto.setCommentType("AGENT");
        dto.setParentId(PARENT_ID);

        service.add(TICKET_ID, dto, OPERATOR_ID);

        ArgumentCaptor<TicketComment> commentCaptor = ArgumentCaptor.forClass(TicketComment.class);
        verify(ticketCommentMapper).insert(commentCaptor.capture());
        assertThat(commentCaptor.getValue().getParentId()).isEqualTo(PARENT_ID);

        ArgumentCaptor<TicketLog> logCaptor = ArgumentCaptor.forClass(TicketLog.class);
        verify(ticketLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getContent())
                .contains("parentId=" + PARENT_ID);
    }

    @Test
    @DisplayName("add parent 不存在：抛 COMMENT_PARENT_INVALID(T0105)，不写库")
    void add_parent_not_found_throws_T0105() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        when(ticketCommentMapper.selectById(PARENT_ID)).thenReturn(null);

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("AGENT");
        dto.setParentId(PARENT_ID);

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.COMMENT_PARENT_INVALID.getCode());
        verify(ticketCommentMapper, never()).insert(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add parent 跨工单：抛 COMMENT_PARENT_INVALID(T0105)")
    void add_parent_belongs_to_other_ticket_throws_T0105() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        TicketComment parent = new TicketComment();
        parent.setId(PARENT_ID);
        parent.setTicketId(999L);
        when(ticketCommentMapper.selectById(PARENT_ID)).thenReturn(parent);

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("AGENT");
        dto.setParentId(PARENT_ID);

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.COMMENT_PARENT_INVALID.getCode());
        verify(ticketCommentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add 工单不存在：抛 TICKET_NOT_FOUND(T0101)")
    void add_ticket_not_found_throws_T0101() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(null);

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("CUSTOMER");

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.TICKET_NOT_FOUND.getCode());
        verify(ticketCommentMapper, never()).insert(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add 工单已关闭：抛 TICKET_CLOSED(T0103)")
    void add_closed_ticket_throws_T0103() {
        baseTicket.setStatus(TicketStatus.CLOSED);
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("CUSTOMER");

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.TICKET_CLOSED.getCode());
        verify(ticketCommentMapper, never()).insert(any());
        verify(ticketLogMapper, never()).insert(any());
    }

    @Test
    @DisplayName("add content 为空：抛 PARAM_INVALID，不查工单")
    void add_blank_content_throws_PARAM_INVALID() {
        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("   ");
        dto.setCommentType("CUSTOMER");

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(ticketInfoMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("add content 超 2000 字符：抛 PARAM_INVALID")
    void add_overlong_content_throws_PARAM_INVALID() {
        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("a".repeat(2001));
        dto.setCommentType("CUSTOMER");

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("add commentType 为空：抛 PARAM_INVALID")
    void add_null_type_throws_PARAM_INVALID() {
        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType(null);

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("add commentType 非法字符串：抛 PARAM_INVALID（C0400）")
    void add_invalid_type_string_throws_PARAM_INVALID() {
        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("FOO_BAR");

        assertThatThrownBy(() -> service.add(TICKET_ID, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("add ticketId 为空：抛 PARAM_INVALID")
    void add_null_ticket_id_throws_PARAM_INVALID() {
        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("x");
        dto.setCommentType("CUSTOMER");

        assertThatThrownBy(() -> service.add(null, dto, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
    }

    @Test
    @DisplayName("add 内容含 HTML 特殊字符：写入前 escape")
    void add_escapes_html_in_content() {
        when(ticketInfoMapper.selectById(TICKET_ID)).thenReturn(baseTicket);
        Mockito.doAnswer(inv -> {
            TicketComment c = inv.getArgument(0);
            c.setId(COMMENT_ID);
            return 1;
        }).when(ticketCommentMapper).insert(any(TicketComment.class));

        TicketCommentCreateDTO dto = new TicketCommentCreateDTO();
        dto.setContent("<script>alert(\"xss\")</script> & 'ok'");
        dto.setCommentType("AGENT");

        service.add(TICKET_ID, dto, OPERATOR_ID);

        ArgumentCaptor<TicketComment> captor = ArgumentCaptor.forClass(TicketComment.class);
        verify(ticketCommentMapper).insert(captor.capture());
        assertThat(captor.getValue().getContent())
                .isEqualTo("&lt;script&gt;alert(&quot;xss&quot;)&lt;/script&gt; &amp; &#x27;ok&#x27;");
    }

    // ==================== list ====================

    @Test
    @DisplayName("list 无 ticket:comment 视角：INTERNAL 评论被过滤")
    void list_filters_internal_for_non_internal_staff() {
        TicketComment customer = newComment(COMMENT_ID, null, CommentType.CUSTOMER, 1L, 1L);
        TicketComment internal = newComment(COMMENT_ID + 1, null, CommentType.INTERNAL, 2L, 2L);
        TicketComment agent = newComment(COMMENT_ID + 2, null, CommentType.AGENT, 3L, 3L);
        when(ticketCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(customer, internal, agent));
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TicketCommentVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TicketCommentVO::getCommentType)
                .containsExactly(CommentType.CUSTOMER, CommentType.AGENT);
    }

    @Test
    @DisplayName("list 拥有 ticket:comment 视角（admin / agent）：可见全部评论（含 INTERNAL）")
    void list_internal_staff_sees_all_comments() {
        securityMock.when(() -> SecurityContextUtils.hasAuthority("ticket:comment")).thenReturn(true);
        TicketComment customer = newComment(COMMENT_ID, null, CommentType.CUSTOMER, 1L, 1L);
        TicketComment internal = newComment(COMMENT_ID + 1, null, CommentType.INTERNAL, 2L, 2L);
        when(ticketCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(customer, internal));
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TicketCommentVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(TicketCommentVO::getCommentType)
                .containsExactly(CommentType.CUSTOMER, CommentType.INTERNAL);
    }

    @Test
    @DisplayName("list 拼装 creatorName：通过 sys_user.nickname 批量填充")
    void list_attaches_creator_name() {
        securityMock.when(() -> SecurityContextUtils.hasAuthority("ticket:comment")).thenReturn(true);
        TicketComment c1 = newComment(COMMENT_ID, null, CommentType.CUSTOMER, 11L, 11L);
        TicketComment c2 = newComment(COMMENT_ID + 1, null, CommentType.AGENT, 22L, 22L);
        when(ticketCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1, c2));

        SysUser u11 = new SysUser();
        u11.setId(11L);
        u11.setNickname("坐席小王");
        SysUser u22 = new SysUser();
        u22.setId(22L);
        u22.setNickname("客服老张");
        when(sysUserMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(u11, u22));

        List<TicketCommentVO> result = service.list(TICKET_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getCreatorName()).isEqualTo("坐席小王");
        assertThat(result.get(1).getCreatorName()).isEqualTo("客服老张");
    }

    @Test
    @DisplayName("list 工单无评论：返回空列表，不查 sys_user")
    void list_empty_returns_empty_list() {
        when(ticketCommentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<TicketCommentVO> result = service.list(TICKET_ID);

        assertThat(result).isEmpty();
        verify(sysUserMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("list ticketId 为空：抛 PARAM_INVALID")
    void list_null_ticket_id_throws_PARAM_INVALID() {
        assertThatThrownBy(() -> service.list(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(ticketCommentMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ==================== delete ====================

    @Test
    @DisplayName("delete 创建者本人：软删 is_deleted=1")
    void delete_by_creator_succeeds() {
        TicketComment existing = newComment(COMMENT_ID, null, CommentType.CUSTOMER, OPERATOR_ID, CREATOR_ID);
        when(ticketCommentMapper.selectById(COMMENT_ID)).thenReturn(existing);
        when(ticketCommentMapper.deleteById(COMMENT_ID)).thenReturn(1);

        service.delete(TICKET_ID, COMMENT_ID, OPERATOR_ID);

        verify(ticketCommentMapper, times(1)).deleteById(COMMENT_ID);
    }

    @Test
    @DisplayName("delete 管理员：可删除他人评论")
    void delete_by_admin_succeeds() {
        securityMock.when(() -> SecurityContextUtils.hasAuthority("admin")).thenReturn(true);
        TicketComment existing = newComment(COMMENT_ID, null, CommentType.AGENT, 99L, 99L);
        when(ticketCommentMapper.selectById(COMMENT_ID)).thenReturn(existing);
        when(ticketCommentMapper.deleteById(COMMENT_ID)).thenReturn(1);

        service.delete(TICKET_ID, COMMENT_ID, OPERATOR_ID);

        verify(ticketCommentMapper, times(1)).deleteById(COMMENT_ID);
    }

    @Test
    @DisplayName("delete 非创建者非管理员：抛 AUTH_FORBIDDEN(403)")
    void delete_by_other_non_admin_throws_FORBIDDEN() {
        TicketComment existing = newComment(COMMENT_ID, null, CommentType.AGENT, 99L, 99L);
        when(ticketCommentMapper.selectById(COMMENT_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(TICKET_ID, COMMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.AUTH_FORBIDDEN.getCode());
        verify(ticketCommentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete 评论不存在：抛 COMMENT_NOT_FOUND(T0104)")
    void delete_nonexistent_comment_throws_T0104() {
        when(ticketCommentMapper.selectById(COMMENT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.delete(TICKET_ID, COMMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.COMMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete 评论跨工单：抛 COMMENT_NOT_FOUND")
    void delete_comment_belongs_to_other_ticket_throws_T0104() {
        TicketComment existing = newComment(COMMENT_ID, null, CommentType.AGENT, OPERATOR_ID, OPERATOR_ID);
        existing.setTicketId(999L);
        when(ticketCommentMapper.selectById(COMMENT_ID)).thenReturn(existing);

        assertThatThrownBy(() -> service.delete(TICKET_ID, COMMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.COMMENT_NOT_FOUND.getCode());
    }

    @Test
    @DisplayName("delete ticketId / commentId 为空：抛 PARAM_INVALID")
    void delete_null_ids_throw_PARAM_INVALID() {
        assertThatThrownBy(() -> service.delete(null, COMMENT_ID, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        assertThatThrownBy(() -> service.delete(TICKET_ID, null, OPERATOR_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(ticketCommentMapper, never()).selectById(any());
    }

    // ==================== escapeHtml 工具 ====================

    @Test
    @DisplayName("escapeHtml 五个特殊字符均被替换")
    void escapeHtml_replaces_all_special_chars() {
        assertThat(TicketCommentServiceImpl.escapeHtml("<a href=\"x\">'b' & c</a>"))
                .isEqualTo("&lt;a href=&quot;x&quot;&gt;&#x27;b&#x27; &amp; c&lt;/a&gt;");
    }

    @Test
    @DisplayName("escapeHtml null 输入返回 null")
    void escapeHtml_null_returns_null() {
        assertThat(TicketCommentServiceImpl.escapeHtml(null)).isNull();
    }

    @Test
    @DisplayName("escapeHtml 无特殊字符返回原串")
    void escapeHtml_plain_text_unchanged() {
        assertThat(TicketCommentServiceImpl.escapeHtml("hello world 中文 123"))
                .isEqualTo("hello world 中文 123");
    }

    // ---------- 辅助 ----------

    private TicketComment newComment(Long id, Long parentId, CommentType type, Long creatorId, Long operatorId) {
        TicketComment c = new TicketComment();
        c.setId(id);
        c.setTicketId(TICKET_ID);
        c.setContent("dummy");
        c.setCommentType(type);
        c.setCreatorId(creatorId);
        c.setParentId(parentId);
        c.setCreateTime(LocalDateTime.of(2026, 8, 11, 12, 0));
        c.setIsDeleted(TicketComment.NOT_DELETED);
        return c;
    }
}
