import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { normalizeRoute, routeLabel, setHashRoute, ROUTES } from './hashRoute.js'

describe('hash route helpers', () => {
  it('normalizes supported hash and path inputs', () => {
    assert.equal(normalizeRoute('#/owner'), ROUTES.OWNER)
    assert.equal(normalizeRoute('/admin'), ROUTES.ADMIN)
    assert.equal(normalizeRoute('station'), ROUTES.STATION)
  })

  it('falls back unknown or blank routes to login', () => {
    assert.equal(normalizeRoute(''), ROUTES.LOGIN)
    assert.equal(normalizeRoute(null), ROUTES.LOGIN)
    assert.equal(normalizeRoute('#/unknown'), ROUTES.LOGIN)
  })

  it('maps routes to workspace labels', () => {
    assert.equal(routeLabel(ROUTES.LOGIN), '登录')
    assert.equal(routeLabel(ROUTES.OWNER), '车主自助')
    assert.equal(routeLabel(ROUTES.ADMIN), '运营管理')
    assert.equal(routeLabel(ROUTES.STATION), '站点运行')
  })

  it('writes normalized hash routes to the current window', () => {
    const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window')

    try {
      Object.defineProperty(globalThis, 'window', {
        configurable: true,
        value: { location: { hash: '' } }
      })

      assert.equal(setHashRoute(ROUTES.OWNER), ROUTES.OWNER)
      assert.equal(globalThis.window.location.hash, '#/owner')

      assert.equal(setHashRoute('#/bad'), ROUTES.LOGIN)
      assert.equal(globalThis.window.location.hash, '#/login')
    } finally {
      if (originalWindow) {
        Object.defineProperty(globalThis, 'window', originalWindow)
      } else {
        delete globalThis.window
      }
    }
  })
})
