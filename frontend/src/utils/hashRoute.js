export const ROUTES = {
  STATION: '/station',
  OWNER: '/owner',
  ADMIN: '/admin'
}

const labels = {
  [ROUTES.STATION]: '站点运行',
  [ROUTES.OWNER]: '车主自助',
  [ROUTES.ADMIN]: '运营管理'
}

export function normalizeRoute(value) {
  const raw = String(value || '').trim().replace(/^#/, '')
  const route = raw.startsWith('/') ? raw : `/${raw}`

  return Object.values(ROUTES).includes(route) ? route : ROUTES.STATION
}

export function routeLabel(route) {
  return labels[normalizeRoute(route)]
}

export function setHashRoute(route, targetWindow = globalThis.window) {
  const normalized = normalizeRoute(route)
  if (targetWindow?.location) {
    targetWindow.location.hash = `#${normalized}`
  }
  return normalized
}
