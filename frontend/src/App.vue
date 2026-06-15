<template>
  <LoginView v-if="activeRoute === ROUTES.LOGIN || !session" @authenticated="handleAuthenticated" />

  <RoleWorkspaceShell
    v-else
    :session="session"
    :active-route="activeRoute"
    @navigate="navigateTo"
    @logout="logout"
  >
    <SimulationSandbox v-if="activeRoute === ROUTES.STATION" />
    <OwnerPanel v-else-if="activeRoute === ROUTES.OWNER" :session="session" />
    <AdminPanel v-else />
  </RoleWorkspaceShell>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import RoleWorkspaceShell from './components/shell/RoleWorkspaceShell.vue'
import SimulationSandbox from './views/SimulationSandbox.vue'
import OwnerPanel from './views/OwnerPanel.vue'
import AdminPanel from './views/AdminPanel.vue'
import LoginView from './views/LoginView.vue'
import { api } from './api/chargingApi'
import { clearAuthSession, defaultRouteForSession, loadAuthSession } from './utils/authSession'
import { ROUTES, normalizeRoute, setHashRoute } from './utils/hashRoute'

const session = ref(loadAuthSession())
const activeRoute = ref(normalizeRoute(window.location.hash))

function routeAllowed(route, currentSession) {
  if (!currentSession) {
    return route === ROUTES.LOGIN
  }
  if (route === ROUTES.LOGIN) {
    return false
  }
  if (route === ROUTES.OWNER) {
    return currentSession.role === 'OWNER'
  }
  if (route === ROUTES.ADMIN) {
    return currentSession.role === 'ADMIN'
  }
  return route === ROUTES.STATION
}

function syncRoute() {
  const normalized = normalizeRoute(window.location.hash)
  const fallback = defaultRouteForSession(session.value)
  const nextRoute = routeAllowed(normalized, session.value) ? normalized : fallback
  activeRoute.value = nextRoute

  if (window.location.hash !== `#${nextRoute}`) {
    setHashRoute(nextRoute)
  }
}

function navigateTo(route) {
  const normalized = normalizeRoute(route)
  if (!routeAllowed(normalized, session.value)) {
    activeRoute.value = setHashRoute(defaultRouteForSession(session.value))
    return
  }
  activeRoute.value = setHashRoute(normalized)
}

function handleAuthenticated({ session: nextSession, route }) {
  session.value = nextSession
  activeRoute.value = setHashRoute(route)
}

async function logout() {
  try {
    await api.logout()
  } catch {
    // Local logout is valid even if the backend session already expired.
  }
  clearAuthSession()
  session.value = null
  activeRoute.value = setHashRoute(ROUTES.LOGIN)
}

onMounted(() => {
  syncRoute()
  window.addEventListener('hashchange', syncRoute)
})

onBeforeUnmount(() => {
  window.removeEventListener('hashchange', syncRoute)
})
</script>
