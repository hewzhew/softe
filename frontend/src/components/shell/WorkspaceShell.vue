<template>
  <div class="workspace-shell">
    <aside class="workspace-sidebar">
      <div class="workspace-brand">
        <strong>波普特大学充电站</strong>
        <span>调度计费系统</span>
      </div>

      <nav class="workspace-nav" aria-label="工作区导航">
        <button
          v-for="item in navItems"
          :key="item.route"
          class="workspace-nav-item"
          :class="{ active: item.route === normalizedRoute }"
          :aria-current="item.route === normalizedRoute ? 'page' : undefined"
          type="button"
          @click="$emit('navigate', item.route)"
        >
          <span class="workspace-nav-mark">{{ item.mark }}</span>
          <span>
            <strong>{{ routeLabel(item.route) }}</strong>
            <small>{{ item.description }}</small>
          </span>
        </button>
      </nav>
    </aside>

    <section class="workspace-main">
      <header class="workspace-header">
        <div>
          <p class="workspace-eyebrow">{{ routeLabel(normalizedRoute) }}</p>
          <h1>{{ activeTitle }}</h1>
        </div>
        <el-tag effect="plain">H2 本机数据</el-tag>
      </header>

      <main class="workspace-content">
        <slot />
      </main>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { ROUTES, normalizeRoute, routeLabel } from '../../utils/hashRoute'

defineEmits(['navigate'])

const props = defineProps({
  activeRoute: {
    type: String,
    default: ROUTES.STATION
  }
})

const navItems = [
  {
    route: ROUTES.STATION,
    mark: 'S',
    description: '仿真时钟、事件队列与站点态势'
  },
  {
    route: ROUTES.OWNER,
    mark: 'O',
    description: '车辆建档、排队进度与账单查询'
  },
  {
    route: ROUTES.ADMIN,
    mark: 'A',
    description: '参数配置、故障处置与运营校验'
  }
]

const titles = {
  [ROUTES.STATION]: '站点运行控制台',
  [ROUTES.OWNER]: '车主自助门户',
  [ROUTES.ADMIN]: '运营管理工作台'
}

const normalizedRoute = computed(() => normalizeRoute(props.activeRoute))
const activeTitle = computed(() => titles[normalizedRoute.value])
</script>
