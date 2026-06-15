import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { normalizeRoute, routeLabel, ROUTES } from './hashRoute.js'

describe('hash route helpers', () => {
  it('normalizes supported hash and path inputs', () => {
    assert.equal(normalizeRoute('#/owner'), ROUTES.OWNER)
    assert.equal(normalizeRoute('/admin'), ROUTES.ADMIN)
    assert.equal(normalizeRoute('station'), ROUTES.STATION)
  })

  it('falls back unknown or blank routes to station', () => {
    assert.equal(normalizeRoute(''), ROUTES.STATION)
    assert.equal(normalizeRoute(null), ROUTES.STATION)
    assert.equal(normalizeRoute('#/unknown'), ROUTES.STATION)
  })

  it('maps routes to workspace labels', () => {
    assert.equal(routeLabel(ROUTES.OWNER), '车主自助')
    assert.equal(routeLabel(ROUTES.ADMIN), '运营管理')
    assert.equal(routeLabel(ROUTES.STATION), '站点运行')
  })
})
