export const AUTH_SESSION_KEY = 'charging-auth-session'

export function loadAuthSession(storage = globalThis.sessionStorage) {
  if (!storage) {
    return null
  }
  const raw = storage.getItem(AUTH_SESSION_KEY)
  if (!raw) {
    return null
  }
  try {
    const session = JSON.parse(raw)
    return session?.token && session?.role ? session : null
  } catch {
    return null
  }
}

export function saveAuthSession(session, storage = globalThis.sessionStorage) {
  if (!storage) {
    return
  }
  storage.setItem(AUTH_SESSION_KEY, JSON.stringify(session))
}

export function clearAuthSession(storage = globalThis.sessionStorage) {
  if (!storage) {
    return
  }
  storage.removeItem(AUTH_SESSION_KEY)
}

export function defaultRouteForSession(session) {
  if (session?.role === 'OWNER') {
    return '/owner'
  }
  if (session?.role === 'ADMIN') {
    return '/admin'
  }
  return '/login'
}
