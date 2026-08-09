package com.ticket.system.service.assembler;

import com.ticket.system.entity.SysMenu;
import com.ticket.system.vo.SysMenuTreeVO;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 菜单树组装器 —— 纯函数，无状态，专门为单测设计（详见 ticket 03 验收标准）。
 * <p>
 * 输入：扁平菜单行集合（任意顺序）；输出：树形结构。
 * <p>
 * 排序策略：同 parent 内按 {@code sort} 升序、其次按 {@code id} 升序；
 * 让"原始数据没排序"也能输出稳定顺序，方便单测断言。
 */
public final class MenuTreeAssembler {

    private MenuTreeAssembler() {
        // 工具类不允许实例化
    }

    /**
     * 把扁平菜单列表组装成树（仅顶级节点列表）。
     *
     * @param menus 任意顺序的菜单行；为 null 时返回空列表
     */
    public static List<SysMenuTreeVO> assemble(List<SysMenu> menus) {
        if (menus == null || menus.isEmpty()) {
            return new ArrayList<>();
        }

        // 一次性映射为 VO 节点，并按 id 建索引
        Map<Long, SysMenuTreeVO> nodeIndex = new HashMap<>(menus.size());
        for (SysMenu menu : menus) {
            nodeIndex.put(menu.getId(), SysMenuTreeVO.from(menu));
        }

        // 按 parentId 把节点挂到父节点的 children 上（顶级节点独立收集）
        List<SysMenuTreeVO> roots = new ArrayList<>();
        for (SysMenu menu : menus) {
            SysMenuTreeVO node = nodeIndex.get(menu.getId());
            Long parentId = menu.getParentId();
            SysMenuTreeVO parent = parentId == null ? null : nodeIndex.get(parentId);
            if (parent == null) {
                // parentId == 0（顶级）或指向不存在的 id：当作顶级
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        // 排序 + 递归排序子节点 —— 同样按 sort asc, id asc
        Comparator<SysMenuTreeVO> order = Comparator
                .comparing(SysMenuTreeVO::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(SysMenuTreeVO::getId, Comparator.nullsLast(Comparator.naturalOrder()));
        roots.sort(order);
        for (SysMenuTreeVO root : roots) {
            sortRecursively(root, order);
        }
        return roots;
    }

    private static void sortRecursively(SysMenuTreeVO node, Comparator<SysMenuTreeVO> order) {
        if (node.getChildren() == null || node.getChildren().isEmpty()) {
            return;
        }
        node.getChildren().sort(order);
        for (SysMenuTreeVO child : node.getChildren()) {
            sortRecursively(child, order);
        }
    }
}