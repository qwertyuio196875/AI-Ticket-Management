package com.ticket.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ticket.common.exception.BusinessException;
import com.ticket.common.exception.BusinessExceptionCode;
import com.ticket.system.dto.SysUserAssignRolesDTO;
import com.ticket.system.dto.SysUserSaveDTO;
import com.ticket.system.entity.SysMenu;
import com.ticket.system.entity.SysRole;
import com.ticket.system.entity.SysUser;
import com.ticket.system.entity.SysUserRole;
import com.ticket.system.mapper.SysMenuMapper;
import com.ticket.system.mapper.SysRoleMapper;
import com.ticket.system.mapper.SysRoleMenuMapper;
import com.ticket.system.mapper.SysUserMapper;
import com.ticket.system.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SysUserServiceImpl} 单元测试 —— ticket 03 验收标准。
 * <p>
 * 不启动 Spring 上下文，纯 Mockito，覆盖关键业务规则：
 * 创建 BCrypt 密码、用户名冲突转业务异常、"最后一个超管"保护、权限查询链路。
 */
@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock SysUserMapper userMapper;
    @Mock SysUserRoleMapper userRoleMapper;
    @Mock SysRoleMapper roleMapper;
    @Mock SysRoleMenuMapper roleMenuMapper;
    @Mock SysMenuMapper menuMapper;
    @Mock PasswordEncoder passwordEncoder;

    @InjectMocks SysUserServiceImpl service;

    private SysUser adminUser;

    @BeforeEach
    void setUp() {
        adminUser = new SysUser();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setNickname("超级管理员");
        adminUser.setStatus(SysUser.STATUS_ENABLED);
    }

    // ---------- getById ----------

    @Test
    void getById_returns_user_when_present() {
        when(userMapper.selectById(1L)).thenReturn(adminUser);
        SysUser result = service.getById(1L);
        assertThat(result.getUsername()).isEqualTo("admin");
    }

    @Test
    void getById_throws_USER_NOT_FOUND_when_absent() {
        when(userMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.getById(99L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.USER_NOT_FOUND.getCode());
    }

    // ---------- create ----------

    @Test
    void create_bcrypts_password_and_returns_id() {
        SysUserSaveDTO dto = new SysUserSaveDTO();
        dto.setUsername("alice");
        dto.setPassword("plain-pass");
        dto.setNickname("Alice");
        dto.setStatus(SysUser.STATUS_ENABLED);

        when(passwordEncoder.encode("plain-pass")).thenReturn("bcrypt-hash");
        // userMapper.insert 后 id 会被设置 —— 用 Answer 写入
        org.mockito.Mockito.doAnswer(inv -> {
            SysUser u = inv.getArgument(0);
            u.setId(100L);
            return 1;
        }).when(userMapper).insert(any(SysUser.class));

        Long id = service.create(dto);
        assertThat(id).isEqualTo(100L);

        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).insert(captor.capture());
        SysUser inserted = captor.getValue();
        assertThat(inserted.getUsername()).isEqualTo("alice");
        assertThat(inserted.getPassword()).isEqualTo("bcrypt-hash");
        assertThat(inserted.getStatus()).isEqualTo(SysUser.STATUS_ENABLED);
    }

    @Test
    void create_translates_username_collision_to_USER_DUPLICATE() {
        SysUserSaveDTO dto = new SysUserSaveDTO();
        dto.setUsername("admin");
        dto.setPassword("x");
        dto.setNickname("n");
        when(passwordEncoder.encode("x")).thenReturn("h");
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk"))
                .when(userMapper).insert(any(SysUser.class));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.USER_DUPLICATE.getCode());
    }

    @Test
    void create_rejects_blank_password() {
        SysUserSaveDTO dto = new SysUserSaveDTO();
        dto.setUsername("u");
        dto.setPassword("  ");
        dto.setNickname("n");

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.PARAM_INVALID.getCode());
        verify(userMapper, never()).insert(any());
    }

    // ---------- update ----------

    @Test
    void update_keeps_existing_password_when_blank() {
        SysUserSaveDTO dto = new SysUserSaveDTO();
        dto.setId(1L);
        dto.setNickname("新昵称");
        dto.setPassword(""); // 留空 = 不动密码
        dto.setStatus(1);
        when(userMapper.selectById(1L)).thenReturn(adminUser);

        service.update(dto);

        // password 字段应当保持原值
        assertThat(adminUser.getPassword()).isNull(); // adminUser 初始化时未设
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    void update_bcrypts_new_password_when_provided() {
        SysUserSaveDTO dto = new SysUserSaveDTO();
        dto.setId(1L);
        dto.setNickname("x");
        dto.setPassword("new-pass");
        when(userMapper.selectById(1L)).thenReturn(adminUser);
        when(passwordEncoder.encode("new-pass")).thenReturn("new-hash");

        service.update(dto);

        assertThat(adminUser.getPassword()).isEqualTo("new-hash");
    }

    // ---------- delete ----------

    @Test
    void delete_throws_LAST_ADMIN_PROTECTED_when_user_is_the_only_admin() {
        when(userMapper.selectById(1L)).thenReturn(adminUser);
        // admin 用户当前只有 roleId=1
        when(userRoleMapper.findRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        // resolveRoleKey(1L) 走 roleMapper.selectById(1L) → admin
        when(roleMapper.selectById(1L)).thenReturn(roleOf(1L, "admin"));
        // resolveRoleIdByKey("admin") 走 roleMapper.selectOne → 返回 admin role
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(roleOf(1L, "admin"));
        // countUsersInRole(roleId=1) → selectCount → 全系统只有 1 个 admin
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.LAST_ADMIN_PROTECTED.getCode());
        verify(userMapper, never()).deleteById(anyLong());
    }

    @Test
    void delete_succeeds_when_user_is_not_admin() {
        SysUser another = new SysUser();
        another.setId(1L);
        another.setUsername("alice");
        when(userMapper.selectById(1L)).thenReturn(another);
        when(userRoleMapper.findRoleIdsByUserId(1L)).thenReturn(List.of(2L)); // 非 admin 角色
        when(roleMapper.selectById(2L)).thenReturn(roleOf(2L, "agent"));

        service.delete(1L);

        verify(userMapper, times(1)).deleteById(1L);
        verify(userRoleMapper, times(1)).deleteByUserId(1L);
    }

    // ---------- assignRoles ----------

    @Test
    void assignRoles_blocks_admin_self_demotion_when_only_one_admin() {
        SysUserAssignRolesDTO dto = new SysUserAssignRolesDTO();
        dto.setRoleIds(List.of(2L)); // 想去掉 admin
        when(userMapper.selectById(1L)).thenReturn(adminUser);
        // 当前挂 admin
        when(userRoleMapper.findRoleIdsByUserId(1L)).thenReturn(List.of(1L));
        // 用 anyLong + lenient 处理多次动态查询（不同的 roleId）
        lenient().when(roleMapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 1L) return roleOf(1L, "admin");
            if (id == 2L) return roleOf(2L, "agent");
            return null;
        });
        // resolveRoleIdByKey("admin")：selectOne → admin role
        when(roleMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(roleOf(1L, "admin"));
        // 全系统只有 1 个 admin —— 触发保护
        when(userRoleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.assignRoles(1L, dto))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(BusinessExceptionCode.LAST_ADMIN_PROTECTED.getCode());
        verify(userRoleMapper, never()).deleteByUserId(anyLong());
    }

    @Test
    void assignRoles_replaces_role_set_when_user_was_not_admin() {
        SysUserAssignRolesDTO dto = new SysUserAssignRolesDTO();
        dto.setRoleIds(List.of(1L, 2L));
        when(userMapper.selectById(2L)).thenReturn(userOf(2L));
        // 当前用户没挂 admin —— ensureNotLastAdmin 直接 return，不查 roleMapper
        when(userRoleMapper.findRoleIdsByUserId(2L)).thenReturn(List.of(3L));
        // lenient —— assignRoles 还会查 requestedIds 里的 role key
        lenient().when(roleMapper.selectById(anyLong())).thenAnswer(inv -> {
            Long id = inv.getArgument(0);
            if (id == 1L) return roleOf(1L, "admin");
            if (id == 2L) return roleOf(2L, "agent");
            return null;
        });

        service.assignRoles(2L, dto);

        verify(userRoleMapper, times(1)).deleteByUserId(2L);
        verify(userRoleMapper, times(1)).insert(eq(new SysUserRole(2L, 1L)));
        verify(userRoleMapper, times(1)).insert(eq(new SysUserRole(2L, 2L)));
    }

    // ---------- listPermissions ----------

    @Test
    void listPermissions_returns_empty_when_user_has_no_roles() {
        when(userRoleMapper.findRoleIdsByUserId(7L)).thenReturn(List.of());
        assertThat(service.listPermissions(7L)).isEmpty();
        verify(roleMenuMapper, never()).findMenuIdsByRoleIds(any());
    }

    @Test
    void listPermissions_aggregates_unique_non_empty_permissions() {
        when(userRoleMapper.findRoleIdsByUserId(7L)).thenReturn(List.of(10L, 11L));
        when(roleMenuMapper.findMenuIdsByRoleIds(List.of(10L, 11L))).thenReturn(List.of(100L, 101L, 102L));
        SysMenu m1 = menuWithPerm(100L, "ticket:view");
        SysMenu m2 = menuWithPerm(101L, "ticket:create"); // permission 同名（duplicate）
        SysMenu m3 = menuWithPerm(102L, "");              // 空 permission —— 跳过
        when(menuMapper.selectBatchIds(List.of(100L, 101L, 102L))).thenReturn(List.of(m1, m2, m3));

        List<String> perms = service.listPermissions(7L);
        assertThat(perms).containsExactlyInAnyOrder("ticket:view", "ticket:create");
    }

    // ---------- 辅助 ----------

    private static SysRole roleOf(Long id, String key) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleKey(key);
        return r;
    }

    private static SysUser userOf(Long id) {
        SysUser u = new SysUser();
        u.setId(id);
        u.setUsername("u-" + id);
        u.setStatus(SysUser.STATUS_ENABLED);
        return u;
    }

    private static SysMenu menuWithPerm(Long id, String perm) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setPermission(perm);
        return m;
    }
}