import assert from 'node:assert/strict'
import { describe, it } from 'node:test'
import { validateRequestAmount } from './ownerValidation.js'

describe('owner request validation', () => {
  it('rejects request amount greater than vehicle capacity instead of clamping it', () => {
    const result = validateRequestAmount(100, 80)

    assert.equal(result.valid, false)
    assert.equal(result.message, '申请电量不能超过车辆电池容量')
  })

  it('accepts request amount within vehicle capacity', () => {
    const result = validateRequestAmount(30, 80)

    assert.equal(result.valid, true)
  })
})
