<template>
  <WorkspaceShell :active-route="activeRoute" @navigate="navigateTo">
    <SimulationSandbox v-if="activeRoute === ROUTES.STATION" />
    <OwnerPanel v-else-if="activeRoute === ROUTES.OWNER" />
    <AdminPanel v-else />
  </WorkspaceShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import WorkspaceShell from './components/shell/WorkspaceShell.vue'
import SimulationSandbox from './views/SimulationSandbox.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'
import { ROUTES, normalizeRoute, setHashRoute } from './utils/hashRoute'

const activeRoute = ref(normalizeRoute(window.location.hash))

function syncRoute() {
  activeRoute.value = normalizeRoute(window.location.hash)
}

function navigateTo(route) {
  activeRoute.value = setHashRoute(route)
}

onMounted(() => {
  if (!window.location.hash) {
    navigateTo(ROUTES.STATION)
  } else {
    syncRoute()
  }
  window.addEventListener('hashchange', syncRoute)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
})
</script>
