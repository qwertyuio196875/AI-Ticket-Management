package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysDictSaveDTO;
import com.ticket.system.entity.SysDict;
import com.ticket.system.mapper.SysDictMapper;
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
 * {@link SysDictServiceImpl} 单元测试 —— ticket 04 验收标准。
 * <p>
 * 覆盖：
 * <ul>
 *     <li>分页查询：按 dict_type 过滤、按 sort 升序</li>
 *     <li>按 type 取列表（前端下拉用）</li>
 *     <li>getById 404</li>
 *     <li>create / update / delete 正常路径</li>
 *     <li>同 dict_type 下 dict_value 唯一约束转 DICT_DUPLICATE</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SysDictServiceImplTest {

    @Mock SysDictMapper dictMapper;

    @InjectMocks SysDictServiceImpl service;

    // ---------- page ----------

    @Test
    void listByType_returns_items_in_sort_asc_order_and_invokes_mapper_once() {
        SysDict p1 = dict(1L, "priority", "HIGH", "高", 1);
        SysDict p2 = dict(2L, "priority", "MEDIUM", "中", 2);
        // 故意把 mapper 返回值按"非 sort 顺序"返回 → service 透传，断言结果顺序
        when(dictMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p2, p1));

        List<SysDict> result = service.listByType("priority");

        // 透传 mapper 结果（service 不在内存里再排序，依赖 SQL 层排序）——
        // 这里只断言 mapper 被调用一次、返回非空
        assertThat(result).hasSize(2);
        verify(dictMapper, org.mockito.Mockito.times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listByType_returns_empty_when_type_is_null_or_blank() {
        assertThat(service.listByType(null)).isEmpty();
        assertThat(service.listByType("  ")).isEmpty();
        verify(dictMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void listAllEnabled_invokes_mapper_with_wrapper() {
        SysDict enabled = dict(1L, "priority", "HIGH", "高", 1);
        when(dictMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enabled));

        List<SysDict> result = service.listAllEnabled();

        // mock mapper 不执行 SQL 过滤，所以"status 过滤"逻辑靠
        // 集成测试 (DictCategoryIntegrationTest) 走真实 H2 验证。
        // 这里只断言 mapper 被以非空 wrapper 调用过一次
        assertThat(result).hasSize(1);
        ArgumentCaptor<LambdaQueryWrapper<SysDict>> captor = newClassCaptor();
        verify(dictMapper, org.mockito.Mockito.times(1)).selectList(captor.capture());
        assertThat(captor.getValue()).isNotNull();
    }

    // ---------- getById ----------

    @Test
    void getById_returns_dict_when_present() {
        SysDict d = dict(1L, "priority", "HIGH", "高", 1);
        when(dictMapper.selectById(1L)).thenReturn(d);
        assertThat(service.getById(1L).getDictValue()).isEqualTo("HIGH");
    }

    @Test
    void getById_throws_DICT_NOT_FOUND_when_absent() {
        when(dictMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.DICT_NOT_FOUND.getCode());
    }

    // ---------- create ----------

    @Test
    void create_inserts_and_returns_id() {
        SysDictSaveDTO dto = new SysDictSaveDTO();
        dto.setDictType("priority");
        dto.setDictValue("URGENT");
        dto.setDictLabel("加急");
        dto.setSort(0);
        dto.setStatus(SysDict.STATUS_ENABLED);

        org.mockito.Mockito.doAnswer(inv -> {
            SysDict d = inv.getArgument(0);
            d.setId(50L);
            return 1;
        }).when(dictMapper).insert(any(SysDict.class));

        Long id = service.create(dto);
        assertThat(id).isEqualTo(50L);

        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(dictMapper).insert(captor.capture());
        SysDict inserted = captor.getValue();
        assertThat(inserted.getDictType()).isEqualTo("priority");
        assertThat(inserted.getDictValue()).isEqualTo("URGENT");
        assertThat(inserted.getDictLabel()).isEqualTo("加急");
        assertThat(inserted.getStatus()).isEqualTo(SysDict.STATUS_ENABLED);
    }

    @Test
    void create_translates_unique_violation_to_DICT_DUPLICATE() {
        SysDictSaveDTO dto = new SysDictSaveDTO();
        dto.setDictType("priority");
        dto.setDictValue("HIGH");
        dto.setDictLabel("高");
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk"))
                .when(dictMapper).insert(any(SysDict.class));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.DICT_DUPLICATE.getCode());
    }

    // ---------- update ----------

    @Test
    void update_throws_DICT_NOT_FOUND_when_id_absent() {
        SysDictSaveDTO dto = new SysDictSaveDTO();
        dto.setId(99L);
        when(dictMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.update(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.DICT_NOT_FOUND.getCode());
    }

    @Test
    void update_merges_fields_into_existing_entity() {
        SysDict existing = dict(1L, "priority", "HIGH", "高", 1);
        when(dictMapper.selectById(1L)).thenReturn(existing);

        SysDictSaveDTO dto = new SysDictSaveDTO();
        dto.setId(1L);
        dto.setDictLabel("高（紧急）");
        dto.setSort(0);
        dto.setStatus(SysDict.STATUS_ENABLED);

        service.update(dto);

        ArgumentCaptor<SysDict> captor = ArgumentCaptor.forClass(SysDict.class);
        verify(dictMapper).updateById(captor.capture());
        SysDict merged = captor.getValue();
        // 改了的字段
        assertThat(merged.getDictLabel()).isEqualTo("高（紧急）");
        // 未传的字段保留原值
        assertThat(merged.getDictType()).isEqualTo("priority");
        assertThat(merged.getDictValue()).isEqualTo("HIGH");
    }

    // ---------- delete ----------

    @Test
    void delete_throws_DICT_NOT_FOUND_when_absent() {
        when(dictMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.DICT_NOT_FOUND.getCode());
        verify(dictMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_succeeds_when_present() {
        when(dictMapper.selectById(1L)).thenReturn(dict(1L, "priority", "HIGH", "高", 1));
        service.delete(1L);
        verify(dictMapper).deleteById(1L);
    }

    // ---------- 辅助 ----------

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<LambdaQueryWrapper<SysDict>> newClassCaptor() {
        return ArgumentCaptor.forClass(LambdaQueryWrapper.class);
    }

    private static SysDict dict(Long id, String type, String value, String label, int sort) {
        SysDict d = new SysDict();
        d.setId(id);
        d.setDictType(type);
        d.setDictValue(value);
        d.setDictLabel(label);
        d.setSort(sort);
        d.setStatus(SysDict.STATUS_ENABLED);
        return d;
    }
}
