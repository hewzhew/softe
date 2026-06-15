import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import {
  CLOCK_RATES,
  addMinutesToStationTime,
  formatClockStatus,
  formatStationDateTime,
  formatRuntimeEvent,
  runtimeEventAppliedLabel,
  runtimeEventSourceLabel,
  runtimeEventTypeLabel,
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

  it('formats station date time values for runtime controls', () => {
    assert.equal(formatStationDateTime('2026-06-01T06:00:00'), '2026-06-01 06:00:00')
    assert.equal(formatStationDateTime('2026-06-01 06:00:00'), '2026-06-01 06:00:00')
    assert.equal(formatStationDateTime(null), '--')
  })

  it('adds minutes to station local date time values', () => {
    assert.equal(addMinutesToStationTime('2026-06-01T06:00:00', 5), '2026-06-01T06:05:00')
    assert.equal(addMinutesToStationTime('2026-06-01 06:55:00', 10), '2026-06-01T07:05:00')
    assert.equal(addMinutesToStationTime('', 5), '')
    assert.equal(addMinutesToStationTime('2026-06-01T06:00:00', Number.NaN), '')
  })

  it('labels runtime event types sources and applied states', () => {
    assert.equal(runtimeEventTypeLabel('ChargeRequestSubmitted'), '提交申请')
    assert.equal(runtimeEventTypeLabel('RequestedAmountChanged'), '修改电量')
    assert.equal(runtimeEventTypeLabel('PileFaulted'), '充电桩故障')
    assert.equal(runtimeEventTypeLabel('UnknownEvent'), 'UnknownEvent')

    assert.equal(runtimeEventSourceLabel('COURSE_PRESET'), '导入序列')
    assert.equal(runtimeEventSourceLabel('MANUAL_OPERATION'), '人工录入')
    assert.equal(runtimeEventSourceLabel('unknown'), 'unknown')

    assert.equal(runtimeEventAppliedLabel(true), '已生效')
    assert.equal(runtimeEventAppliedLabel(false), '待生效')
  })
})
