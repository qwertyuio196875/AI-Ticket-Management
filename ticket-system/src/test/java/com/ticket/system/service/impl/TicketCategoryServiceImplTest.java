package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.TicketCategorySaveDTO;
import com.ticket.system.entity.TicketCategory;
import com.ticket.system.mapper.TicketCategoryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TicketCategoryServiceImpl} 单元测试 —— ticket 04 验收标准。
 * <p>
 * 覆盖：
 * <ul>
 *     <li>listAllEnabled：按 sort asc、只取 status=1</li>
 *     <li>getById / create / update / delete 正常路径</li>
 *     <li>name 唯一索引冲突转 CATEGORY_DUPLICATE</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TicketCategoryServiceImplTest {

    @Mock TicketCategoryMapper categoryMapper;

    @InjectMocks TicketCategoryServiceImpl service;

    // ---------- listAllEnabled ----------

    @Test
    void listAllEnabled_invokes_mapper_with_wrapper() {
        TicketCategory c1 = category(1L, "系统故障", "desc-1", 1);
        TicketCategory c2 = category(2L, "网络问题", "desc-2", 2);
        when(categoryMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1, c2));

        List<TicketCategory> result = service.listAllEnabled();

        // mock mapper 不执行 SQL 过滤，"status 过滤 + sort 排序"靠
        // 集成测试 (DictCategoryIntegrationTest) 走真实 H2 验证。
        // 这里只断言 mapper 被以非空 wrapper 调用过一次，返回非空
        assertThat(result).hasSize(2);
        ArgumentCaptor<LambdaQueryWrapper<TicketCategory>> captor = newClassCaptor();
        verify(categoryMapper, org.mockito.Mockito.times(1)).selectList(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    // ---------- getById ----------

    @Test
    void getById_returns_category_when_present() {
        TicketCategory c = category(1L, "系统故障", "desc", 1);
        when(categoryMapper.selectById(1L)).thenReturn(c);
        assertThat(service.getById(1L).getName()).isEqualTo("系统故障");
    }

    @Test
    void getById_throws_CATEGORY_NOT_FOUND_when_absent() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.CATEGORY_NOT_FOUND.getCode());
    }

    // ---------- create ----------

    @Test
    void create_inserts_and_returns_id() {
        TicketCategorySaveDTO dto = new TicketCategorySaveDTO();
        dto.setName("硬件问题");
        dto.setDescription("显示器、键盘等硬件");
        dto.setSort(10);
        dto.setStatus(TicketCategory.STATUS_ENABLED);

        org.mockito.Mockito.doAnswer(inv -> {
            TicketCategory c = inv.getArgument(0);
            c.setId(20L);
            return 1;
        }).when(categoryMapper).insert(any(TicketCategory.class));

        Long id = service.create(dto);
        assertThat(id).isEqualTo(20L);

        ArgumentCaptor<TicketCategory> captor = ArgumentCaptor.forClass(TicketCategory.class);
        verify(categoryMapper).insert(captor.capture());
        TicketCategory inserted = captor.getValue();
        assertThat(inserted.getName()).isEqualTo("硬件问题");
        assertThat(inserted.getSort()).isEqualTo(10);
        assertThat(inserted.getStatus()).isEqualTo(TicketCategory.STATUS_ENABLED);
    }

    @Test
    void create_translates_unique_violation_to_CATEGORY_DUPLICATE() {
        TicketCategorySaveDTO dto = new TicketCategorySaveDTO();
        dto.setName("系统故障");
        dto.setSort(1);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk"))
                .when(categoryMapper).insert(any(TicketCategory.class));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.CATEGORY_DUPLICATE.getCode());
    }

    // ---------- update ----------

    @Test
    void update_throws_CATEGORY_NOT_FOUND_when_id_absent() {
        TicketCategorySaveDTO dto = new TicketCategorySaveDTO();
        dto.setId(99L);
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.CATEGORY_NOT_FOUND.getCode());
    }

    @Test
    void update_merges_fields_into_existing_entity() {
        TicketCategory existing = category(1L, "系统故障", "原描述", 1);
        when(categoryMapper.selectById(1L)).thenReturn(existing);

        TicketCategorySaveDTO dto = new TicketCategorySaveDTO();
        dto.setId(1L);
        dto.setName("系统故障");
        dto.setDescription("新描述");
        dto.setSort(2);
        dto.setStatus(TicketCategory.STATUS_ENABLED);

        service.update(dto);

        ArgumentCaptor<TicketCategory> captor = ArgumentCaptor.forClass(TicketCategory.class);
        verify(categoryMapper).updateById(captor.capture());
        TicketCategory merged = captor.getValue();
        assertThat(merged.getDescription()).isEqualTo("新描述");
        assertThat(merged.getSort()).isEqualTo(2);
        // name 字段未变
        assertThat(merged.getName()).isEqualTo("系统故障");
    }

    // ---------- delete ----------

    @Test
    void delete_throws_CATEGORY_NOT_FOUND_when_absent() {
        when(categoryMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.CATEGORY_NOT_FOUND.getCode());
        verify(categoryMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_succeeds_when_present() {
        when(categoryMapper.selectById(1L)).thenReturn(category(1L, "系统故障", "d", 1));
        service.delete(1L);
        verify(categoryMapper).deleteById(1L);
    }

    // ---------- 辅助 ----------

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<TicketCategory>> newClassCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private static TicketCategory category(Long id, String name, String desc, int sort) {
        TicketCategory c = new TicketCategory();
        c.setId(id);
        c.setName(name);
        c.setDescription(desc);
        c.setSort(sort);
        c.setStatus(TicketCategory.STATUS_ENABLED);
        return c;
    }
}
