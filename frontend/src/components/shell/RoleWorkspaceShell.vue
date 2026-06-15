<template>
  <div class="role-shell">
    <aside class="role-sidebar">
      <div class="workspace-brand">
        <strong>波普特大学充电站</strong>
        <span>{{ roleTitle }}</span>
      </div>

      <nav class="workspace-nav" aria-label="工作区导航">
        <button
          v-for="item in navItems"
          :key="item.route"
          class="workspace-nav-item"
          :class="{ active: item.route === activeRoute }"
          type="button"
          @click="$emit('navigate', item.route)"
        >
          <span class="workspace-nav-mark">{{ item.mark }}</span>
          <span>
            <strong>{{ item.label }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>

      <el-button plain @click="$emit('logout')">退出登录</el-button>
    </aside>

    <section class="workspace-main">
      <header class="workspace-header">
        <div>
          <p class="workspace-eyebrow">{{ session.userName }}</p>
          <h1>{{ activeTitle }}</h1>
        </div>
        <el-tag effect="plain">{{ session.role === 'ADMIN' ? '管理员' : '车主' }}</el-tag>
      </header>

      <main class="workspace-content">
        <slot />
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ROUTES } from '../../utils/hashRoute'

defineEmits(['navigate', 'logout'])

const props = defineProps({
  session: {
    type: Object,
    required: true
  },
  activeRoute: {
    type: String,
    required: true
  }
})

const roleTitle = computed(() => (props.session.role === 'ADMIN' ? '运营管理工作台' : '车主自助门户'))

const navItems = computed(() => {
  if (props.session.role === 'ADMIN') {
    return [
      { route: ROUTES.ADMIN, mark: 'A', label: '运营管理', description: '调度、故障、参数与账单' },
      { route: ROUTES.STATION, mark: 'S', label: '站点沙盘', description: '查看统一站点态势' }
    ]
  }
  return [
    { route: ROUTES.OWNER, mark: 'O', label: '车主门户', description: '申请、排队、充电与账单' },
    { route: ROUTES.STATION, mark: 'S', label: '站点概览', description: '查看站点实时状态' }
  ]
})

const activeTitle = computed(() => {
  return navItems.value.find((item) => item.route === props.activeRoute)?.label || roleTitle.value
})
</script>
