import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  CLOCK_RATES,
  formatClockStatus,
  formatRuntimeEvent,
  shouldPollRuntime
} from './stationClock.js'

describe('station clock helpers', () => {
  it('exposes supported playback rates', () => {
    assert.deepEqual(CLOCK_RATES, [1, 5, 10, 60])
  })

  it('formats clock status from running state and rate', () => {
    assert.equal(formatClockStatus({ running: true, rate: 10 }), '运行中 · 10x')
    assert.equal(formatClockStatus({ running: false, rate: 1 }), '已暂停 · 1x')
    assert.equal(formatClockStatus(null), '已暂停 · 1x')
  })

  it('polls while the runtime clock is running', () => {
    assert.equal(shouldPollRuntime({ running: true }), true)
    assert.equal(shouldPollRuntime({ running: false }), false)
    assert.equal(shouldPollRuntime(null), false)
  })

  it('formats runtime events with time, vehicle, and type', () => {
    assert.equal(
      formatRuntimeEvent({
        at: '2026-06-03T06:00:00',
        carId: 'CAR-1',
        type: 'ChargeRequestSubmitted'
      }),
      '06:00:00 CAR-1 ChargeRequestSubmitted'
    )
  })

  it('formats backend runtime event rows', () => {
    assert.equal(
      formatRuntimeEvent({
        eventTime: '2026-06-03T06:00:00',
        targetId: 'CAR-1',
        eventType: 'ChargeRequestSubmitted'
      }),
      '06:00:00 CAR-1 ChargeRequestSubmitted'
    )
  })
})
