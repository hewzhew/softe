import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  AUTH_SESSION_KEY,
  clearAuthSession,
  defaultRouteForSession,
  loadAuthSession,
  saveAuthSession
} from './authSession.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => (values.has(key) ? values.get(key) : null),
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key)
  }
}

describe('auth session helpers', () => {
  it('saves and loads the active tab session', () => {
    const storage = memoryStorage()
    const session = {
      token: 'token-1',
      role: 'OWNER',
      accountId: 1,
      userName: 'Alice',
      carId: 'CAR-1'
    }

    saveAuthSession(session, storage)

    assert.equal(storage.getItem(AUTH_SESSION_KEY).includes('token-1'), true)
    assert.deepEqual(loadAuthSession(storage), session)
  })

  it('clears the active tab session', () => {
    const storage = memoryStorage()
    saveAuthSession({ token: 'token-2', role: 'ADMIN', accountId: 2, userName: 'admin' }, storage)

    clearAuthSession(storage)

    assert.equal(loadAuthSession(storage), null)
  })

  it('routes each role to its workspace', () => {
    assert.equal(defaultRouteForSession(null), '/login')
    assert.equal(defaultRouteForSession({ role: 'OWNER' }), '/owner')
    assert.equal(defaultRouteForSession({ role: 'ADMIN' }), '/admin')
  })
})
