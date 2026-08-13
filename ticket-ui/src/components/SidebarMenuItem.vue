<script setup lang="ts">
import { computed, type Component } from 'vue'
import {
  Odometer,
  User,
  UserFilled,
  Menu,
  Tickets,
  Collection,
  Files,
  Grid,
} from '@element-plus/icons-vue'
import type { MenuNode } from '../api/menus'

const props = defineProps<{ node: MenuNode }>()

/**
 * 菜单图标映射：后端种子数据的 icon 为字符串（dashboard / user / role / menu /
 * ticket / dict / category），此处映射到 @element-plus/icons-vue 组件；
 * 未命中的图标名使用兜底 Grid。
 */
const iconMap: Record<string, Component> = {
  dashboard: Odometer,
  user: User,
  role: UserFilled,
  menu: Menu,
  ticket: Tickets,
  dict: Collection,
  category: Files,
}
const fallbackIcon: Component = Grid

const nodeIcon = computed<Component | undefined>(() =>
  props.node.icon ? iconMap[props.node.icon] ?? fallbackIcon : undefined,
)

/**
 * 可见子项：仅 C 型（有 path 的页面菜单）参与导航渲染；
 * F 型按钮（仅 permission，无 path）不渲染，避免出现空的子菜单。
 */
const visibleChildren = computed(() =>
  (props.node.children ?? []).filter((child) => child.menuType === 'C' && child.path),
)
</script>

<template>
  <!-- F 型按钮权限节点一律不渲染 -->
  <template v-if="node.menuType !== 'F'">
    <!-- 有可见子项 → 渲染子菜单（折叠模式下父级带图标，保证 icon-only 表现自然） -->
    <el-sub-menu v-if="visibleChildren.length > 0" :index="node.path || String(node.id)">
      <template #title>
        <el-icon v-if="nodeIcon"><component :is="nodeIcon" /></el-icon>
        <span>{{ node.menuName }}</span>
      </template>
      <SidebarMenuItem v-for="child in visibleChildren" :key="child.id" :node="child" />
    </el-sub-menu>

    <!-- C 型且有 path → 渲染可导航菜单项 -->
    <el-menu-item v-else-if="node.menuType === 'C' && node.path" :index="node.path">
      <el-icon v-if="nodeIcon"><component :is="nodeIcon" /></el-icon>
      <template #title>{{ node.menuName }}</template>
    </el-menu-item>
  </template>
</template>
