import { afterEach, describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { http, unwrap } from './client.js'
import { api } from './chargingApi.js'
import { saveAuthSession } from '../utils/authSession.js'

const originalAdapter = http.defaults.adapter
const originalSessionStorage = Object.getOwnPropertyDescriptor(globalThis, 'sessionStorage')

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => (values.has(key) ? values.get(key) : null),
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key)
  }
}

function installSessionStorage() {
  const storage = memoryStorage()
  Object.defineProperty(globalThis, 'sessionStorage', {
    configurable: true,
    value: storage
  })
  return storage
}

function restoreSessionStorage() {
  if (originalSessionStorage) {
    Object.defineProperty(globalThis, 'sessionStorage', originalSessionStorage)
  } else {
    delete globalThis.sessionStorage
  }
}

function okResponse(config, data = null) {
  return {
    data: { success: true, data },
    status: 200,
    statusText: 'OK',
    headers: {},
    config,
    request: {}
  }
}

function headerValue(headers, name) {
  if (headers && typeof headers.get === 'function') {
    return headers.get(name)
  }
  return headers ? headers[name] : undefined
}

afterEach(() => {
  http.defaults.adapter = originalAdapter
  restoreSessionStorage()
})

describe('API client auth support', () => {
  it('attaches stored session token to requests', async () => {
    const storage = installSessionStorage()
    saveAuthSession({ token: 'token-1', role: 'OWNER' }, storage)
    let seenHeaders

    http.defaults.adapter = async (config) => {
      seenHeaders = config.headers
      return okResponse(config)
    }

    await unwrap(http.get('/secured'))

    assert.equal(headerValue(seenHeaders, 'X-Session-Token'), 'token-1')
  })

  it('exposes auth API endpoints', async () => {
    const calls = []
    http.defaults.adapter = async (config) => {
      calls.push(config)
      return okResponse(config, { ok: true })
    }

    assert.deepEqual(await api.login({ loginName: 'admin', password: '123456' }), { ok: true })
    assert.deepEqual(await api.currentUser(), { ok: true })
    assert.deepEqual(await api.logout(), { ok: true })

    assert.deepEqual(
      calls.map((call) => ({ method: call.method, url: call.url })),
      [
        { method: 'post', url: '/auth/login' },
        { method: 'get', url: '/auth/me' },
        { method: 'post', url: '/auth/logout' }
      ]
    )

    const loginPayload = typeof calls[0].data === 'string' ? JSON.parse(calls[0].data) : calls[0].data
    assert.deepEqual(loginPayload, { loginName: 'admin', password: '123456' })
  })
})
