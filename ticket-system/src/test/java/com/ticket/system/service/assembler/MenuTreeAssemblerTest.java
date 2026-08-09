package com.ticket.system.service.assembler;

import com.ticket.system.entity.SysMenu;
import com.ticket.system.vo.SysMenuTreeVO;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 菜单树组装器单测 —— ticket 03 验收标准要求"菜单树组装逻辑"的单元覆盖。
 * <p>
 * 这是一个无依赖的纯函数测试，所有边界都靠数据驱动，毫秒级运行。
 */
class MenuTreeAssemblerTest {

    @Test
    void assemble_empty_input_returns_empty_list() {
        assertThat(MenuTreeAssembler.assemble(null)).isEmpty();
        assertThat(MenuTreeAssembler.assemble(List.of())).isEmpty();
    }

    @Test
    void assemble_single_root_node_has_empty_children() {
        SysMenu root = menu(1L, 0L, "Dashboard", "dashboard:view", 1);
        List<SysMenuTreeVO> tree = MenuTreeAssembler.assemble(List.of(root));
        assertThat(tree).hasSize(1);
        SysMenuTreeVO rootVo = tree.get(0);
        assertThat(rootVo.getMenuName()).isEqualTo("Dashboard");
        assertThat(rootVo.getPermission()).isEqualTo("dashboard:view");
        assertThat(rootVo.getChildren()).isEmpty();
    }

    @Test
    void assemble_links_children_to_their_parent() {
        SysMenu dashboard = menu(1L, 0L, "Dashboard", "dashboard:view", 1);
        SysMenu userMgmt = menu(2L, 0L, "用户管理", "user:manage", 2);
        SysMenu userList = menu(10L, 2L, "用户列表", "user:list", 1);
        SysMenu userAdd = menu(11L, 2L, "新建用户", "user:create", 2);
        SysMenu roleMgmt = menu(3L, 0L, "角色管理", "role:manage", 3);

        // 输入故意乱序 —— 组装器应当处理任意顺序
        List<SysMenu> input = new ArrayList<>(List.of(roleMgmt, userAdd, dashboard, userList, userMgmt));
        List<SysMenuTreeVO> tree = MenuTreeAssembler.assemble(input);

        assertThat(tree).hasSize(3);
        // 顶级按 sort asc: Dashboard(1) / 用户管理(2) / 角色管理(3)
        assertThat(tree.get(0).getMenuName()).isEqualTo("Dashboard");
        assertThat(tree.get(1).getMenuName()).isEqualTo("用户管理");
        assertThat(tree.get(2).getMenuName()).isEqualTo("角色管理");

        // "用户管理" 下应有 2 个子菜单，按 sort asc: 用户列表(1) / 新建用户(2)
        SysMenuTreeVO userMgmtVo = tree.get(1);
        assertThat(userMgmtVo.getChildren()).hasSize(2);
        assertThat(userMgmtVo.getChildren().get(0).getMenuName()).isEqualTo("用户列表");
        assertThat(userMgmtVo.getChildren().get(1).getMenuName()).isEqualTo("新建用户");

        // Dashboard / 角色管理 无子菜单
        assertThat(tree.get(0).getChildren()).isEmpty();
        assertThat(tree.get(2).getChildren()).isEmpty();
    }

    @Test
    void assemble_treats_orphan_parent_id_as_root() {
        // 顶级 parentId 是 0；但如果有个节点的 parentId 指向不存在的 id（数据脏），
        // 组装器应当把它当作顶级，而不是丢失或抛异常
        SysMenu dirtyNode = menu(99L, 999L, "脏数据", "dirty:perm", 0);
        List<SysMenuTreeVO> tree = MenuTreeAssembler.assemble(List.of(dirtyNode));
        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getId()).isEqualTo(99L);
    }

    @Test
    void assemble_uses_id_as_tiebreaker_when_sort_is_equal() {
        SysMenu a = menu(1L, 0L, "A", "a", 1);
        SysMenu b = menu(2L, 0L, "B", "b", 1);
        List<SysMenu> input = new ArrayList<>(List.of(b, a)); // 故意反序
        List<SysMenuTreeVO> tree = MenuTreeAssembler.assemble(input);
        // sort 相同 → 按 id 升序：A(1) → B(2)
        assertThat(tree.get(0).getMenuName()).isEqualTo("A");
        assertThat(tree.get(1).getMenuName()).isEqualTo("B");
    }

    // ---------- 辅助 ----------

    private static SysMenu menu(Long id, Long parentId, String name, String perm, int sort) {
        SysMenu m = new SysMenu();
        m.setId(id);
        m.setParentId(parentId);
        m.setMenuName(name);
        m.setMenuType("C");
        m.setPermission(perm);
        m.setSort(sort);
        m.setVisible(1);
        return m;
    }
}